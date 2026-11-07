// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** The three opt-in per-package folders on the owner's primary shared storage. */
final class VaultExternal {
    enum Part {
        DATA("data"), OBB("obb"), MEDIA("media");

        final String directory;
        Part(String directory) { this.directory = directory; }
    }

    static final String PERMISSION = "android.permission.ACCESS_MTP";
    private static final String INDEX = "paths";
    private static final int MAX_PATH_BYTES = 4096;
    private static final int MAX_INDEX_BYTES = 4 * 1024 * 1024;
    private static final long RESERVE = 128L * 1024 * 1024;
    private static final LinkOption NO_LINKS = LinkOption.NOFOLLOW_LINKS;

    private VaultExternal() {}

    static final class PartialRestore extends IOException {
        final int restored;
        final int total;
        PartialRestore(int restored, int total, IOException cause) {
            super("Shared storage restore was interrupted", cause);
            this.restored = restored;
            this.total = total;
        }
    }

    static Path staged(Path root, String pkg, Part part) {
        if (pkg == null || !pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) {
            throw new IllegalArgumentException("Invalid package for shared storage");
        }
        return root.resolve("external").resolve(pkg).resolve(part.directory);
    }

    static int capture(Path shared, Path staging, String pkg, Part part, VaultArchive.Progress progress)
            throws IOException {
        Path target = staged(staging, pkg, part);
        Path source = directory(shared, Path.of("Android", part.directory, pkg), false);
        if (source == null) return 0;
        List<Path> paths = new ArrayList<>();
        Set<String> names = new HashSet<>();
        byte[] buffer = new byte[64 * 1024];
        Files.createDirectories(target);
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                private long total;
                private int indexSize = Integer.BYTES;

                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    progress.bytes(total);
                    if (part == Part.DATA && source.relativize(dir).toString().equalsIgnoreCase("cache")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (!dir.equals(source)) checkPath(source.relativize(dir).toString());
                    return FileVisitResult.CONTINUE;
                }

                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    if (!attrs.isRegularFile()) throw new IOException("Unsupported shared storage file");
                    Path relative = checkPath(source.relativize(file).toString());
                    if (part == Part.DATA && relative.getName(0).toString().equalsIgnoreCase("cache")) {
                        return FileVisitResult.CONTINUE;
                    }
                    indexSize += Integer.BYTES + relative.toString().getBytes(StandardCharsets.UTF_8).length;
                    if (indexSize > MAX_INDEX_BYTES || paths.size() >= VaultArchive.MAX_FILES - 1
                            || attrs.size() > VaultArchive.MAX_BYTES - total
                            || !names.add(relative.toString().toLowerCase(Locale.ROOT))) {
                        throw new IOException("Shared storage folder exceeds archive limits");
                    }
                    checkSpace(staging, attrs.size());
                    copy(file, target.resolve(Integer.toString(paths.size())), attrs.size(), buffer, progress);
                    BasicFileAttributes after = Files.readAttributes(file, BasicFileAttributes.class, NO_LINKS);
                    if (!after.isRegularFile() || after.size() != attrs.size()
                            || !after.lastModifiedTime().equals(attrs.lastModifiedTime())) {
                        throw new IOException("Shared storage file changed during backup");
                    }
                    total += attrs.size();
                    paths.add(relative);
                    return FileVisitResult.CONTINUE;
                }
            });
            if (paths.isEmpty()) {
                Files.delete(target);
                return 0;
            }
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(target.resolve(INDEX))))) {
                out.writeInt(paths.size());
                for (Path path : paths) {
                    byte[] name = path.toString().getBytes(StandardCharsets.UTF_8);
                    out.writeInt(name.length);
                    out.write(name);
                }
            }
            return paths.size();
        } catch (IOException | RuntimeException failure) {
            try {
                VaultArchive.delete(target.toFile());
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    static List<Path> readIndex(Path folder, Part part) throws IOException {
        Path index = folder.resolve(INDEX);
        if (!Files.isDirectory(folder, NO_LINKS) || !Files.isRegularFile(index, NO_LINKS)
                || Files.size(index) > MAX_INDEX_BYTES) {
            throw new IOException("Missing or oversized shared storage index");
        }
        List<Path> paths = new ArrayList<>();
        Set<String> names = new HashSet<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(index, NO_LINKS)))) {
            int count = in.readInt();
            if (count <= 0 || count >= VaultArchive.MAX_FILES) throw new IOException("Invalid shared storage index");
            for (int i = 0; i < count; i++) {
                int size = in.readInt();
                if (size <= 0 || size > MAX_PATH_BYTES) throw new IOException("Invalid shared storage path length");
                byte[] bytes = new byte[size];
                in.readFully(bytes);
                String name = new String(bytes, StandardCharsets.UTF_8);
                Path path = checkPath(name);
                if (!Arrays.equals(bytes, name.getBytes(StandardCharsets.UTF_8))
                        || !names.add(name.toLowerCase(Locale.ROOT))
                        || part == Part.DATA && path.getName(0).toString().equalsIgnoreCase("cache")
                        || !Files.isRegularFile(folder.resolve(Integer.toString(i)), NO_LINKS)) {
                    throw new IOException("Invalid shared storage payload");
                }
                paths.add(path);
            }
            if (in.read() != -1) throw new IOException("Trailing shared storage index data");
        }
        try (Stream<Path> entries = Files.list(folder)) {
            if (entries.count() != paths.size() + 1L) throw new IOException("Unexpected shared storage payload");
        }
        for (Path path : paths) {
            for (Path parent = path.getParent(); parent != null; parent = parent.getParent()) {
                if (names.contains(parent.toString().toLowerCase(Locale.ROOT))) {
                    throw new IOException("Shared storage file shadows a directory");
                }
            }
        }
        return paths;
    }

    static int restore(Path shared, Path staging, String pkg, Part part, VaultArchive.Progress progress)
            throws IOException {
        Path folder = staged(staging, pkg, part);
        List<Path> paths = readIndex(folder, part);
        Path target = directory(shared, Path.of("Android", part.directory, pkg), true);
        byte[] buffer = new byte[64 * 1024];
        int restored = 0;
        try {
            for (int i = 0; i < paths.size(); i++) {
                progress.bytes(restored);
                Path relative = paths.get(i);
                Path parent = directory(target, relative.getParent(), true);
                Path file = parent.resolve(relative.getFileName());
                if (!Files.notExists(file, NO_LINKS) && !Files.isRegularFile(file, NO_LINKS)) {
                    throw new IOException("Shared storage restore target is not a regular file");
                }
                Path source = folder.resolve(Integer.toString(i));
                long bytes = Files.size(source);
                checkSpace(shared, bytes);
                Path temporary = Files.createTempFile(parent, ".hecker-vault-", ".tmp");
                try {
                    copy(source, temporary, bytes, buffer, progress);
                    progress.bytes(restored);
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temporary);
                }
                restored++;
            }
        } catch (IOException failure) {
            throw new PartialRestore(restored, paths.size(), failure);
        }
        return restored;
    }

    private static Path checkPath(String name) throws IOException {
        if (name.isEmpty() || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0
                || name.getBytes(StandardCharsets.UTF_8).length > MAX_PATH_BYTES) {
            throw new IOException("Unsafe shared storage path");
        }
        for (String segment : name.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IOException("Unsafe shared storage path");
            }
        }
        return Path.of(name);
    }

    private static Path directory(Path root, Path relative, boolean create) throws IOException {
        Path path = root;
        if (relative == null) return path;
        for (Path segment : relative) {
            path = path.resolve(segment);
            try {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, NO_LINKS);
                if (!attrs.isDirectory()) throw new IOException("Shared storage path is not a directory");
            } catch (NoSuchFileException absent) {
                if (!create) return null;
                try {
                    Files.createDirectory(path);
                } catch (FileAlreadyExistsException concurrentCreate) {
                    // Another writer can create this folder before we do. Still check its type.
                }
                if (!Files.readAttributes(path, BasicFileAttributes.class, NO_LINKS).isDirectory()) {
                    throw new IOException("Shared storage path is not a directory");
                }
            }
        }
        return path;
    }

    private static void copy(Path source, Path target, long expected, byte[] buffer, VaultArchive.Progress progress)
            throws IOException {
        try (InputStream in = Files.newInputStream(source, NO_LINKS);
                OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING, NO_LINKS)) {
            long copied = 0;
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (count > expected - copied) throw new IOException("Shared storage file grew during backup");
                progress.bytes(copied);
                out.write(buffer, 0, count);
                copied += count;
            }
            if (copied != expected) throw new IOException("Shared storage file size changed");
        }
    }

    private static void checkSpace(Path root, long size) throws IOException {
        if (root.toFile().getUsableSpace() - RESERVE < size) {
            throw new IOException("Not enough free space for shared storage files");
        }
    }
}

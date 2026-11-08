// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

/** Numbered private blobs keep real app filenames outside the public ZIP namespace. */
public final class VaultFileData {
    private static final int MAGIC = 0x48565031, MAX_NODES = 50000;
    public static final class Summary {
        public final int files; public final long bytes;
        Summary(int files, long bytes) { this.files = files; this.bytes = bytes; }
    }
    private static final class Node {
        String path; boolean directory, executable; long size, modified; int blob;
    }
    private static String path(String name) throws IOException {
        if (name == null || name.getBytes(StandardCharsets.UTF_8).length > 4096 || name.indexOf('\0') >= 0 || name.startsWith("/")) throw new IOException("Invalid private file path");
        String[] parts = name.split("/", -1);
        if (parts.length > 128) throw new IOException("Private path nesting limit exceeded");
        if (!parts[0].equals("ce") && !parts[0].equals("de")) throw new IOException("Unknown private storage domain");
        for (String part : parts) if (part.isEmpty() || part.equals(".") || part.equals("..") || part.codePoints().anyMatch(Character::isISOControl)) throw new IOException("Unsafe private file name");
        if (parts.length > 1 && (Set.of("cache", "code_cache", "lib").contains(parts[1]) || parts[1].startsWith(".heckervault-"))) throw new IOException("Reserved private folder in snapshot");
        return name;
    }
    private static Path blob(Path root, int id) { return root.resolve("blobs/" + String.format(Locale.ROOT, "%08d", id)); }
    public static Summary pack(Path raw, String pkg, Path output, VaultArchive.Progress progress) throws IOException {
        if (!VaultFilesProtocol.packageName(pkg) || Files.exists(output, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid private staging");
        Files.createDirectories(output.resolve("blobs")); List<Node> nodes = new ArrayList<>(); long[] bytes = {0}; int[] files = {0};
        for (String domain : List.of("ce", "de")) {
            Path folder = raw.resolve(domain).resolve(pkg);
            if (domain.equals("ce") && !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing captured private app directory");
            if (Files.notExists(folder, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(folder);
            collect(folder, folder, domain, output, nodes, files, bytes, progress);
        }
        try (FileOutputStream file = new FileOutputStream(output.resolve("index.bin").toFile()); DataOutputStream out = new DataOutputStream(file)) {
            out.writeInt(MAGIC); out.writeInt(nodes.size());
            for (Node n : nodes) {
                byte[] name = n.path.getBytes(StandardCharsets.UTF_8);
                out.writeShort(name.length); out.write(name); out.writeBoolean(n.directory); out.writeBoolean(n.executable);
                out.writeLong(n.modified); out.writeInt(n.blob); out.writeLong(n.size);
            }
            out.flush(); file.getFD().sync();
        }
        return validate(output);
    }
    private static void collect(Path base, Path current, String domain, Path output, List<Node> nodes,
            int[] files, long[] bytes, VaultArchive.Progress progress) throws IOException {
        progress.bytes(bytes[0]);
        if (Files.isSymbolicLink(current)) throw new IOException("Private links are not portable");
        Node n = new Node(); String relative = base.equals(current) ? "" : base.relativize(current).toString();
        n.path = path(domain + (relative.isEmpty() ? "" : "/" + relative));
        n.directory = Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS); n.modified = Files.getLastModifiedTime(current, LinkOption.NOFOLLOW_LINKS).toMillis();
        if (nodes.size() >= MAX_NODES) throw new IOException("Too many private entries"); nodes.add(n);
        if (n.directory) {
            List<Path> children = new ArrayList<>(); try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) { for (Path p : stream) children.add(p); }
            children.sort(Comparator.comparing(p -> p.getFileName().toString()));
            for (Path child : children) collect(base, child, domain, output, nodes, files, bytes, progress);
        } else {
            if (!Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS) || ++files[0] > VaultArchive.MAX_FILES - 8) throw new IOException("Unsupported private file");
            n.size = Files.size(current); bytes[0] = Math.addExact(bytes[0], n.size);
            if (bytes[0] > VaultArchive.MAX_BYTES) throw new IOException("Private data exceeds archive limit");
            n.executable = Files.getPosixFilePermissions(current).contains(PosixFilePermission.OWNER_EXECUTE); n.blob = files[0];
            Files.move(current, blob(output, n.blob));
            Files.setPosixFilePermissions(blob(output, n.blob), java.nio.file.attribute.PosixFilePermissions.fromString(n.executable ? "rwx------" : "rw-------"));
        }
    }
    private static List<Node> index(Path root) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(root.resolve("blobs"), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid private payload directory");
        try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
            for (Path child : children) if (!Set.of("index.bin", "blobs").contains(child.getFileName().toString())) throw new IOException("Unexpected private data section");
        }
        Path file = root.resolve("index.bin");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 4 * 1024 * 1024) throw new IOException("Missing private data index");
        List<Node> nodes = new ArrayList<>(); Set<String> directories = new HashSet<>(), names = new HashSet<>(); int blobs = 0; long total = 0;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC) throw new IOException("Unsupported private data index");
            int count = in.readInt(); if (count < 2 || count > MAX_NODES) throw new IOException("Invalid private entry count");
            for (int i = 0; i < count; i++) {
                int length = in.readUnsignedShort(); if (length == 0 || length > 4096) throw new IOException("Invalid private path length");
                byte[] name = new byte[length]; in.readFully(name); String decoded = new String(name, StandardCharsets.UTF_8);
                if (!Arrays.equals(decoded.getBytes(StandardCharsets.UTF_8), name)) throw new IOException("Invalid private filename encoding");
                Node n = new Node(); n.path = path(decoded); n.directory = in.readBoolean(); n.executable = in.readBoolean();
                n.modified = in.readLong(); n.blob = in.readInt(); n.size = in.readLong();
                if (!names.add(n.path) || n.modified < 0) throw new IOException("Duplicate or invalid private entry");
                int slash = n.path.lastIndexOf('/');
                if (slash >= 0 && !directories.contains(n.path.substring(0, slash))) throw new IOException("Missing private parent");
                if (n.directory) {
                    if (n.blob != 0 || n.size != 0 || n.executable) throw new IOException("Invalid private directory metadata");
                    directories.add(n.path);
                } else {
                    if (slash < 0 || n.blob != ++blobs || n.size < 0) throw new IOException("Invalid private blob");
                    Path payload = blob(root, n.blob);
                    if (!Files.isRegularFile(payload, LinkOption.NOFOLLOW_LINKS) || Files.size(payload) != n.size) throw new IOException("Missing private file payload");
                    total = Math.addExact(total, n.size); if (total > VaultArchive.MAX_BYTES) throw new IOException("Private data exceeds limit");
                }
                nodes.add(n);
            }
            if (in.read() != -1 || !directories.containsAll(List.of("ce", "de"))) throw new IOException("Incomplete private data index");
        }
        try (DirectoryStream<Path> payloads = Files.newDirectoryStream(root.resolve("blobs"))) {
            int found = 0; for (Path p : payloads) { if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) || !p.getFileName().toString().matches("[0-9]{8}")) throw new IOException("Unexpected private payload"); found++; }
            if (found != blobs) throw new IOException("Undeclared private payload");
        }
        return nodes;
    }
    public static Summary validate(Path root) throws IOException {
        int count = 0; long bytes = 0; for (Node n : index(root)) if (!n.directory) { count++; bytes += n.size; }
        return new Summary(count, bytes);
    }
    public static void unpack(Path root, String pkg, Path destination, VaultArchive.Progress progress) throws IOException {
        if (!VaultFilesProtocol.packageName(pkg) || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid private restore staging");
        List<Node> nodes = index(root); Files.createDirectories(destination);
        long bytes = 0;
        for (Node n : nodes) {
            String domain = n.path.substring(0, 2), rest = n.path.length() == 2 ? "" : n.path.substring(3);
            Path target = destination.resolve(domain).resolve(pkg).resolve(rest);
            if (n.directory) Files.createDirectories(target);
            else {
                Files.copy(blob(root, n.blob), target);
                Files.setPosixFilePermissions(target, java.nio.file.attribute.PosixFilePermissions.fromString(n.executable ? "rwx------" : "rw-------"));
                bytes += n.size; progress.bytes(bytes);
            }
        }
        // Children first, so directory timestamps are not changed by later creation.
        Collections.reverse(nodes);
        for (Node n : nodes) Files.setLastModifiedTime(destination.resolve(n.path.substring(0, 2)).resolve(pkg)
                .resolve(n.path.length() == 2 ? "" : n.path.substring(3)), java.nio.file.attribute.FileTime.fromMillis(n.modified));
    }
}

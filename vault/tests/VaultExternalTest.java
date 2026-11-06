// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class VaultExternalTest {
    private static final String APP = "org.hecker.sample";
    private static int checks;

    private static void check(boolean result) {
        checks++;
        if (!result) throw new AssertionError("Shared storage check " + checks);
    }

    private static Path write(Path folder, String name, String content) throws IOException {
        Path file = folder.resolve(name);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private static void badIndex(Path folder, String... paths) throws IOException {
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(folder.resolve("paths")))) {
            out.writeInt(paths.length);
            for (String path : paths) {
                byte[] name = path.getBytes(StandardCharsets.UTF_8);
                out.writeInt(name.length);
                out.write(name);
            }
        }
        try {
            VaultExternal.readIndex(folder, VaultExternal.Part.DATA);
            throw new AssertionError("Unsafe shared storage index was accepted");
        } catch (IOException expected) {
            checks++;
        }
    }

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("vault-shared-test");
        try {
            Path shared = Files.createDirectory(work.resolve("source"));
            Path stage = Files.createDirectory(work.resolve("stage"));
            Path output = Files.createDirectory(work.resolve("target"));
            String title = "My clips/نمونہ 1.mp4";
            write(shared, "Android/data/" + APP + "/" + title, "app external data");
            write(shared, "Android/data/" + APP + "/cache/rebuild.me", "cache");
            write(shared, "Android/data/org.other.sample/do-not-copy", "outside selection");
            write(shared, "Android/obb/" + APP + "/main.1.obb", "expansion data");
            write(shared, "Android/media/" + APP + "/music.mp3", "app media");

            for (VaultExternal.Part part : VaultExternal.Part.values()) {
                check(VaultExternal.capture(shared, stage, APP, part, n -> {}) == 1);
            }
            check(VaultExternal.capture(shared, stage, "org.hecker.absent", VaultExternal.Part.DATA, n -> {}) == 0);
            check(!Files.exists(stage.resolve("external/org.other.sample")));
            Path data = VaultExternal.staged(stage, APP, VaultExternal.Part.DATA);
            check(VaultExternal.readIndex(data, VaultExternal.Part.DATA).equals(List.of(Path.of(title))));
            Files.writeString(stage.resolve("snapshot.json"), "{}");
            ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
            char[] password = "HeckerVault test archive".toCharArray();
            VaultArchive.write(stage.toFile(), encrypted, password, n -> {});
            Path inspected = Files.createDirectory(work.resolve("inspect"));
            VaultArchive.read(new ByteArrayInputStream(encrypted.toByteArray()), inspected.toFile(), password,
                    1024 * 1024, n -> {});

            Path previous = write(output, "Android/data/" + APP + "/" + title, "old file");
            Path untouched = write(output, "Android/data/" + APP + "/unselected.txt", "preserve");
            int[] operations = {0};
            try {
                VaultExternal.restore(output, inspected, APP, VaultExternal.Part.DATA, n -> {
                    if (++operations[0] == 3) throw new IOException("Cancelled before replacement");
                });
                throw new AssertionError("Expected restore cancellation");
            } catch (VaultExternal.PartialRestore cancelled) {
                check(cancelled.restored == 0 && cancelled.total == 1);
                check(Files.readString(previous).equals("old file"));
                try (var entries = Files.list(previous.getParent())) {
                    check(entries.count() == 1);
                }
            }
            for (VaultExternal.Part part : VaultExternal.Part.values()) {
                check(VaultExternal.restore(output, inspected, APP, part, n -> {}) == 1);
            }
            check(Files.readString(previous).equals("app external data"));
            check(Files.readString(untouched).equals("preserve"));
            check(Files.readString(output.resolve("Android/obb/" + APP + "/main.1.obb")).equals("expansion data"));
            check(Files.readString(output.resolve("Android/media/" + APP + "/music.mp3")).equals("app media"));
            check(!Files.exists(output.resolve("Android/data/" + APP + "/cache")));

            Path unsafe = Files.createDirectory(work.resolve("unsafe"));
            Files.writeString(unsafe.resolve("0"), "");
            badIndex(unsafe, "../other");
            badIndex(unsafe, "/absolute/path");
            badIndex(unsafe, "folder/../app");
            badIndex(unsafe, "cache/file");
            Files.writeString(unsafe.resolve("1"), "");
            badIndex(unsafe, "File", "file");
            badIndex(unsafe, "file", "file/child");

            Path outside = Files.createDirectory(work.resolve("outside"));
            Path blocked = output.resolve("Android/media/" + APP + "/music.mp3");
            Files.delete(blocked);
            Files.createSymbolicLink(blocked, outside.resolve("other"));
            try {
                VaultExternal.restore(output, inspected, APP, VaultExternal.Part.MEDIA, n -> {});
                throw new AssertionError("Restored through a symbolic link");
            } catch (VaultExternal.PartialRestore rejected) {
                check(rejected.restored == 0 && !Files.exists(outside.resolve("other")));
            } finally {
                Files.delete(blocked);
            }
            System.out.println("HeckerVault shared storage capture/restore checks: " + checks + " PASS");
        } finally {
            VaultArchive.delete(work.toFile());
        }
    }
}

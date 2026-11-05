// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Byte-bounded document copy; never persist external picker identifiers. */
final class DepthPickerCopy {
    static long copy(InputStream source, File destination, long limit) throws IOException {
        if (source == null) throw new IOException("No source stream from photo picker");
        if (destination == null || limit <= 0) throw new IllegalArgumentException("Invalid copy limit");
        long count = 0;
        boolean completed = false;
        try (FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int size;
            while ((size = source.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Copy cancelled");
                if (count > limit - size) throw new IOException("Selected photo is too large");
                output.write(buffer, 0, size);
                count += size;
            }
            if (count == 0) throw new IOException("Empty selected photo");
            output.getFD().sync();
            completed = true;
            return count;
        } finally {
            if (!completed) destination.delete();
        }
    }
    private DepthPickerCopy() {}
}

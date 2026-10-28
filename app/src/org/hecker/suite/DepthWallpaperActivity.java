// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Imports a prepared, aligned pair once. Decoding/compression never run on the UI thread. */
public final class DepthWallpaperActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Uri background, foreground;
    private Bitmap back, front;
    private ImageView preview;
    private Button apply;
    private int generation;
    private boolean stopped;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            String b = state.getString("background"), f = state.getString("foreground");
            if (b != null) background = Uri.parse(b);
            if (f != null) foreground = Uri.parse(f);
        }
        LinearLayout page = Ui.page(this, R.string.depth_wallpaper_title);
        page.addView(Ui.text(this, getString(R.string.depth_wallpaper_import_help), 15, false));
        Ui.actionCard(this, page, R.string.depth_pick_background, R.string.depth_background_summary, v -> pick(1));
        Ui.actionCard(this, page, R.string.depth_pick_foreground, R.string.depth_foreground_summary, v -> pick(2));
        preview = new ImageView(this); preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setContentDescription(getString(R.string.depth_wallpaper_preview));
        page.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(this, 260)));
        apply = new Button(this); apply.setText(R.string.depth_wallpaper_apply); apply.setEnabled(false);
        apply.setOnClickListener(v -> save()); page.addView(apply);
        Ui.scroll(this, page);
        prepare();
    }
    private void pick(int request) {
        Intent document = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(document, request); }
        catch (android.content.ActivityNotFoundException unavailable) { error(R.string.native_page_unavailable); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if ((request != 1 && request != 2) || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == 1) background = uri; else foreground = uri;
        prepare();
    }
    private Bitmap decode(Uri uri) throws IOException {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri),
                (decoder, info, source) -> {
                    int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                    if (w <= 0 || h <= 0 || w > 20000 || h > 20000 || (long) w * h > 100000000)
                        throw new IllegalArgumentException("Unsupported image size");
                    float scale = Math.min(1, 1600f / Math.max(w, h));
                    decoder.setTargetSize(Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
    }
    private void prepare() {
        int token = ++generation;
        back = null; front = null; apply.setEnabled(false); preview.setImageDrawable(null);
        if (background == null || foreground == null) return;
        Uri b = background, f = foreground;
        worker.execute(() -> {
            try {
                Bitmap bg = decode(b), fg = decode(f);
                if (bg.getWidth() != fg.getWidth() || bg.getHeight() != fg.getHeight() || !fg.hasAlpha())
                    throw new IllegalArgumentException("The foreground must retain its background canvas");
                int transparent = 0, opaque = 0;
                for (int y = 0; y < fg.getHeight(); y += Math.max(1, fg.getHeight() / 64))
                    for (int x = 0; x < fg.getWidth(); x += Math.max(1, fg.getWidth() / 64)) {
                        int alpha = fg.getPixel(x, y) >>> 24;
                        if (alpha < 32) transparent++;
                        if (alpha > 224) opaque++;
                    }
                if (transparent < 16 || opaque < 16) throw new IllegalArgumentException("No usable subject mask");
                Bitmap image = Bitmap.createBitmap(bg.getWidth(), bg.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(image); canvas.drawBitmap(bg, 0, 0, null); canvas.drawBitmap(fg, 0, 0, null);
                runOnUiThread(() -> {
                    if (stopped || generation != token) return;
                    back = bg; front = fg; preview.setImageBitmap(image); apply.setEnabled(true);
                });
            } catch (IOException | RuntimeException | OutOfMemoryError invalid) {
                runOnUiThread(() -> { if (!stopped && token == generation) error(R.string.depth_wallpaper_invalid); });
            }
        });
    }
    private void save() {
        if (back == null || front == null) return;
        apply.setEnabled(false);
        Bitmap bg = back, fg = front;
        int token = ++generation;
        worker.execute(() -> {
            String id = UUID.randomUUID().toString().replace("-", "");
            File directory = new File(new File(getFilesDir(), "depth"), id);
            boolean committed = false;
            try {
                if (!directory.mkdirs()) throw new IOException("Cannot create depth pair");
                write(new File(directory, "background.png"), bg);
                write(new File(directory, "foreground.png"), fg);
                if (Thread.currentThread().isInterrupted()) throw new IOException("Import cancelled");
                // Publish the identity last: readers can never observe half of a new pair.
                if (!Settings.Secure.putInt(getContentResolver(), "hecker_custom_lockscreen_clock", 1)
                        || !Settings.Secure.putInt(getContentResolver(), "hecker_lockscreen_clock_style", 2)
                        || !Settings.Secure.putInt(getContentResolver(), "hecker_depth_wallpaper", 1)
                        || !Settings.Secure.putString(getContentResolver(), DepthWallpaperProvider.ID, id))
                    throw new IOException("Cannot publish depth pair");
                committed = true;
                File[] previous = directory.getParentFile().listFiles();
                if (previous != null) for (File old : previous)
                    if (!old.equals(directory) && old.getName().matches("[a-f0-9]{32}")) erase(old);
                runOnUiThread(() -> { if (!stopped && generation == token) { error(R.string.depth_wallpaper_applied); finish(); } });
            } catch (IOException | RuntimeException failure) {
                runOnUiThread(() -> { if (!stopped && generation == token) { error(R.string.depth_wallpaper_invalid); apply.setEnabled(true); } });
            } finally { if (!committed) erase(directory); }
        });
    }
    private static void write(File file, Bitmap bitmap) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Cannot encode depth layer");
            output.getFD().sync();
        }
    }
    private static void erase(File directory) {
        File[] children = directory.listFiles();
        if (children != null) for (File child : children) child.delete();
        directory.delete();
    }
    private void error(int message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (background != null) state.putString("background", background.toString());
        if (foreground != null) state.putString("foreground", foreground.toString());
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        stopped = true; generation++; worker.shutdownNow();
        preview.setImageDrawable(null); back = null; front = null;
        super.onDestroy();
    }
}

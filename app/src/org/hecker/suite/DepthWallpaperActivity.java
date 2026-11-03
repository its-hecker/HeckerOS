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
    private Bitmap back, front, sourceFront;
    private ImageView preview;
    private Button apply;
    private android.widget.Switch home;
    private int generation;
    private boolean stopped,homeOnly;
    private boolean extractSubject;
    private int subjectSize = 100, subjectX, subjectY;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            String b = state.getString("background"), f = state.getString("foreground");
            if (b != null) background = Uri.parse(b);
            if (f != null) foreground = Uri.parse(f);
            extractSubject = state.getBoolean("extract_subject", false);
            subjectSize = state.getInt("subject_size", 100);
            subjectX = state.getInt("subject_x", 0); subjectY = state.getInt("subject_y", 0);
        }
        homeOnly=getIntent().getBooleanExtra("home_only",false);
        LinearLayout page = Ui.page(this, homeOnly?R.string.home_depth_picker_title:R.string.depth_wallpaper_title);
        page.addView(Ui.text(this, getString(homeOnly ? R.string.home_depth_import_help
                : R.string.depth_wallpaper_import_help), 15, false));
        home = new android.widget.Switch(this); home.setText(R.string.depth_apply_home);
        home.setChecked(homeOnly || state != null && state.getBoolean("apply_home", false)); home.setVisibility(homeOnly?android.view.View.GONE:android.view.View.VISIBLE); page.addView(home);
        Ui.actionCard(this, page, R.string.depth_pick_background, R.string.depth_background_summary, v -> pick(1));
        Ui.actionCard(this, page, R.string.depth_extract_title, R.string.depth_extract_summary, v -> pick(3));
        Ui.actionCard(this, page, R.string.depth_pick_foreground, R.string.depth_foreground_summary, v -> pick(2));
        preview = new ImageView(this); preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setContentDescription(getString(R.string.depth_wallpaper_preview));
        page.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(this, 260)));
        page.addView(Ui.text(this, getString(R.string.depth_alignment_help), 14, false));
        alignment(page, R.string.depth_subject_size, 25, 200, subjectSize, value -> subjectSize = value);
        alignment(page, R.string.depth_subject_x, -100, 100, subjectX, value -> subjectX = value);
        alignment(page, R.string.depth_subject_y, -100, 100, subjectY, value -> subjectY = value);
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
        if ((request != 1 && request != 2 && request != 3) || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri,
                data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException temporaryGrant) { /* The picker may supply a task-lifetime grant. */ }
        if (request == 3) { background = uri; foreground = null; extractSubject = true; }
        else { extractSubject = false; if (request == 1) background = uri; else foreground = uri; }
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
        back = null; front = null; sourceFront = null; apply.setEnabled(false); preview.setImageDrawable(null);
        if (background == null || !extractSubject && foreground == null) return;
        Uri b = background, f = foreground;
        boolean automatic = extractSubject;
        worker.execute(() -> {
            try {
                Bitmap bg = decode(b), raw = automatic ? DepthSubjectExtractor.extract(this, bg) : decode(f);
                if (!raw.hasAlpha()) throw new IllegalArgumentException("Select a transparent subject PNG");
                int transparent = 0, opaque = 0;
                for (int y = 0; y < raw.getHeight(); y += Math.max(1, raw.getHeight() / 64))
                    for (int x = 0; x < raw.getWidth(); x += Math.max(1, raw.getWidth() / 64)) {
                        int alpha = raw.getPixel(x, y) >>> 24;
                        if (alpha < 32) transparent++;
                        if (alpha > 224) opaque++;
                    }
                if (transparent < 16 || opaque < 16) throw new IllegalArgumentException("No usable subject mask");
                Bitmap fg = aligned(bg, raw, 100, 0, 0);
                Bitmap image = Bitmap.createBitmap(bg.getWidth(), bg.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(image); canvas.drawBitmap(bg, 0, 0, null); canvas.drawBitmap(fg, 0, 0, null);
                runOnUiThread(() -> {
                    if (stopped || generation != token) return;
                    back = bg; sourceFront = raw; front = fg; preview.setImageBitmap(image); apply.setEnabled(true);
                    if (subjectSize != 100 || subjectX != 0 || subjectY != 0) align();
                });
            } catch (IOException | RuntimeException | OutOfMemoryError invalid) {
                android.util.Log.w("HeckerDepth", "Cannot prepare selected depth images", invalid);
                runOnUiThread(() -> { if (!stopped && token == generation) error(R.string.depth_wallpaper_invalid); });
            }
        });
    }
    private interface AlignmentChange { void set(int value); }
    private void alignment(LinearLayout page, int title, int min, int max, int initial, AlignmentChange changed) {
        android.widget.TextView label = Ui.text(this, getString(title) + " · " + initial, 15, true);
        page.addView(label);
        android.widget.SeekBar slider = new android.widget.SeekBar(this);
        slider.setMax(max - min); slider.setProgress(initial - min);
        slider.setContentDescription(getString(title)); page.addView(slider);
        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(android.widget.SeekBar bar) { }
            public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean user) {
                label.setText(getString(title) + " · " + (progress + min));
                if (user) changed.set(progress + min);
            }
            public void onStopTrackingTouch(android.widget.SeekBar bar) { align(); }
        });
    }
    private static Bitmap aligned(Bitmap background, Bitmap source, int size, int x, int y) {
        float[] transform = DepthLayerGeometry.fit(background.getWidth(), background.getHeight(),
                source.getWidth(), source.getHeight(), size, x, y);
        Bitmap result = Bitmap.createBitmap(background.getWidth(), background.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.translate(transform[1], transform[2]); canvas.scale(transform[0], transform[0]);
        canvas.drawBitmap(source, 0, 0, new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG
                | android.graphics.Paint.FILTER_BITMAP_FLAG));
        return result;
    }
    private void align() {
        if (back == null || sourceFront == null) return;
        int token = ++generation, size = subjectSize, x = subjectX, y = subjectY;
        Bitmap bg = back, raw = sourceFront; apply.setEnabled(false);
        worker.execute(() -> {
            try {
                if (stopped || token != generation) return;
                Bitmap fg = aligned(bg, raw, size, x, y);
                Bitmap image = Bitmap.createBitmap(bg.getWidth(), bg.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(image); canvas.drawBitmap(bg, 0, 0, null); canvas.drawBitmap(fg, 0, 0, null);
                runOnUiThread(() -> { if (!stopped && token == generation) {
                    front = fg; preview.setImageBitmap(image); apply.setEnabled(true);
                } });
            } catch (RuntimeException | OutOfMemoryError failure) {
                runOnUiThread(() -> { if (!stopped && token == generation) error(R.string.depth_wallpaper_invalid); });
            }
        });
    }
    private void save() {
        if (back == null || front == null) return;
        apply.setEnabled(false);
        if (!homeOnly) { Settings.Secure.putInt(getContentResolver(), DepthWallpaperJob.AUTO, 0); DepthWallpaperJob.schedule(this); }
        Bitmap bg = back, fg = front;
        boolean applyHome = home.isChecked();
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
                Bitmap image = Bitmap.createBitmap(bg.getWidth(), bg.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas imageCanvas = new Canvas(image); imageCanvas.drawBitmap(bg, 0, 0, null); imageCanvas.drawBitmap(fg, 0, 0, null);
                android.app.WallpaperManager manager = android.app.WallpaperManager.getInstance(this);
                boolean lockOwned=Settings.Secure.getInt(getContentResolver(),DefaultAppearance.WALLPAPER_ID,-1)==DefaultAppearance.wallpaperId(this);
                manager.setBitmap(image, null, true, homeOnly?android.app.WallpaperManager.FLAG_SYSTEM:
                    android.app.WallpaperManager.FLAG_LOCK|(applyHome?android.app.WallpaperManager.FLAG_SYSTEM:0));
                // Each display has its own identity. A lock-only import cannot disable or erase Home.
                boolean saved=true;
                if(homeOnly||applyHome)saved=Settings.Secure.putInt(getContentResolver(),DefaultAppearance.HOME_WALLPAPER_ID,manager.getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM))
                    &&Settings.Secure.putInt(getContentResolver(),DefaultAppearance.HOME_DEPTH,1)
                    &&Settings.Secure.putString(getContentResolver(),HomeAppearance.KEY,id);
                if(!homeOnly)saved=saved&&Settings.Secure.putInt(getContentResolver(),DefaultAppearance.WALLPAPER_ID,DefaultAppearance.wallpaperId(this))
                    &&Settings.Secure.putInt(getContentResolver(),"hecker_custom_lockscreen_clock",1)
                    &&Settings.Secure.putInt(getContentResolver(),"hecker_depth_wallpaper",1)
                    &&Settings.Secure.putString(getContentResolver(),DepthWallpaperProvider.ID,id);
                else if(lockOwned)saved=saved&&Settings.Secure.putInt(getContentResolver(),DefaultAppearance.WALLPAPER_ID,DefaultAppearance.wallpaperId(this));
                if(!saved)throw new IOException("Cannot publish depth pair");
                committed = true;
                File[] previous = directory.getParentFile().listFiles();
                if (previous != null) for (File old : previous)
                    if (!old.equals(directory) && old.getName().matches("[a-f0-9]{32}")
                            && !old.getName().equals(Settings.Secure.getString(getContentResolver(),DepthWallpaperProvider.ID))
                            && !old.getName().equals(Settings.Secure.getString(getContentResolver(),HomeAppearance.KEY))) erase(old);
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
        state.putBoolean("apply_home", home.isChecked());
        state.putBoolean("extract_subject", extractSubject);
        state.putInt("subject_size", subjectSize); state.putInt("subject_x", subjectX); state.putInt("subject_y", subjectY);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        stopped = true; generation++; worker.shutdownNow();
        preview.setImageDrawable(null); back = null; front = null; sourceFront = null;
        super.onDestroy();
    }
}

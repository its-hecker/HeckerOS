/* Copyright (C) 2025-2026 AxionOS, 2024-2026 Lunaris OS, 2026 HeckerOS.
 * SPDX-License-Identifier: Apache-2.0
 * Adapted from Lunaris PortraitSegmenter's coarse-mask pipeline, with bounded Java buffers.
 */
package org.hecker.suite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

/** Runs once on a worker for a wallpaper, never during drawing or every wake. */
final class DepthSubjectExtractor {
    static Bitmap extract(Context context, Bitmap original) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
        byte[] model;
        try (InputStream input = context.getAssets().open("depth/subject.tflite")) { model = input.readAllBytes(); }
        if (model.length > 20_000_000 || model.length < 1024) throw new IOException("Invalid segmentation model");
        ByteBuffer mapped = ByteBuffer.allocateDirect(model.length).order(ByteOrder.nativeOrder());
        mapped.put(model).rewind();
        try (Interpreter interpreter = new Interpreter(mapped, new Interpreter.Options().setNumThreads(2))) {
            Tensor input = interpreter.getInputTensor(0);
            int[] shape = input.shape();
            if (input.dataType() != DataType.FLOAT32 || shape.length != 4 || shape[0] != 1
                    || shape[3] != 3 || shape[1] > 1024 || shape[2] > 1024) throw new IOException("Unsupported model input");
            int width = shape[2], height = shape[1];
            Bitmap small = Bitmap.createScaledBitmap(original, width, height, true);
            int[] pixels = new int[width * height]; small.getPixels(pixels, 0, width, 0, 0, width, height);
            if (small != original) small.recycle();
            ByteBuffer image = ByteBuffer.allocateDirect(input.numBytes()).order(ByteOrder.nativeOrder());
            for (int pixel : pixels) {
                image.putFloat((pixel >> 16) & 255); image.putFloat((pixel >> 8) & 255); image.putFloat(pixel & 255);
            }
            image.rewind(); Map<Integer, Object> outputs = new HashMap<>();
            for (int i = 0; i < interpreter.getOutputTensorCount(); i++) {
                int size = interpreter.getOutputTensor(i).numBytes();
                if (size < 1 || size > 16_777_216) throw new IOException("Unbounded model output");
                outputs.put(i, ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder()));
            }
            interpreter.runForMultipleInputsOutputs(new Object[]{image}, outputs);
            if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
            Tensor maskTensor = interpreter.getOutputTensor(1); int[] maskShape = maskTensor.shape();
            if (maskTensor.dataType() != DataType.FLOAT32 || maskShape.length != 4 || maskShape[3] != 1)
                throw new IOException("Unsupported model mask");
            int mw = maskShape[2], mh = maskShape[1];
            ByteBuffer values = (ByteBuffer) outputs.get(1); values.rewind();
            int[] mask = new int[mw * mh]; int clear = 0, solid = 0;
            for (int i = 0; i < mask.length; i++) {
                float raw = values.getFloat();
                float alpha = Float.isFinite(raw) ? Math.max(0, Math.min(1, raw)) : 0;
                if (alpha < .05f) alpha = 0; if (alpha > .95f) alpha = 1;
                int a = Math.round(alpha * 255); mask[i] = (a << 24) | 0x00ffffff;
                if (a < 32) clear++; if (a > 224) solid++;
            }
            if (clear < mask.length / 200 || solid < mask.length / 200) throw new IOException("No distinct subject");
            Bitmap low = Bitmap.createBitmap(mask, mw, mh, Bitmap.Config.ARGB_8888);
            Bitmap result = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(low, null, new android.graphics.Rect(0,0,result.getWidth(),result.getHeight()), paint);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN)); canvas.drawBitmap(original,0,0,paint);
            low.recycle(); return result;
        } catch (RuntimeException | LinkageError failure) { throw new IOException("Subject extraction unavailable", failure); }
    }
    private DepthSubjectExtractor() { }
}

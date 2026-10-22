// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import com.android.launcher3.R;
import com.android.launcher3.graphics.DrawableFactory;

import java.util.concurrent.ExecutionException;

/** Keeps the launcher resource-selected icon factory without removed override utility classes. */
public final class HeckerDrawableFactoryProvider {
    // Access is confined to the main thread, including background callers of get().
    private DrawableFactory value;

    public DrawableFactory get(Context context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            try {
                return MAIN_EXECUTOR.submit(() -> get(context)).get();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while creating the icon factory", failure);
            } catch (ExecutionException failure) {
                throw new IllegalStateException("Unable to create the icon factory", failure.getCause());
            }
        }
        if (value == null) {
            Context app = context.getApplicationContext();
            String name = app.getString(R.string.drawable_factory_class);
            if (!name.isEmpty()) {
                try {
                    value = app.getClassLoader().loadClass(name).asSubclass(DrawableFactory.class)
                            .getConstructor(Context.class).newInstance(app);
                } catch (ReflectiveOperationException | ClassCastException failure) {
                    Log.w("HeckerLauncher", "Unable to load the configured icon factory", failure);
                }
            }
            if (value == null) value = new DrawableFactory();
        }
        return value;
    }
}

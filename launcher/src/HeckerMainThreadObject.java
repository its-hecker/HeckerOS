// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.os.Looper;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

/** An application-scoped, main-thread singleton for non-Dagger launcher dependencies. */
public final class HeckerMainThreadObject<T> {
    @FunctionalInterface
    public interface ObjectProvider<T> {
        T get(Context context);
    }

    private final ObjectProvider<T> provider;
    // Only the main thread reads or initializes the cached value.
    private T value;

    public HeckerMainThreadObject(ObjectProvider<T> provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    public T get(Context context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            try {
                return MAIN_EXECUTOR.submit(() -> get(context)).get();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while initializing the launcher", failure);
            } catch (ExecutionException failure) {
                throw new IllegalStateException("Unable to initialize the launcher", failure.getCause());
            }
        }
        if (value == null) value = Objects.requireNonNull(provider.get(context.getApplicationContext()));
        return value;
    }
}

# SPDX-License-Identifier: Apache-2.0
"""Compile and exercise launcher adapters against narrow Android API contracts."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

STUBS = {
    'android/content/Context.java': '''package android.content;
import android.content.pm.PackageManager;
import android.os.UserHandle;
public abstract class Context {
    public Context getApplicationContext() { return this; }
    public ClassLoader getClassLoader() { return getClass().getClassLoader(); }
    public abstract String getString(int id);
    public abstract PackageManager getPackageManager();
    public abstract Context createContextAsUser(UserHandle user, int flags);
}''',
    'android/content/pm/ApplicationInfo.java': '''package android.content.pm;
public class ApplicationInfo { public boolean isAppLockEnabled; }''',
    'android/content/pm/PackageManager.java': '''package android.content.pm;
public abstract class PackageManager {
    public static final long GET_APP_LOCK_INFO = 1L << 35;
    public static final class ApplicationInfoFlags {
        public final long value;
        private ApplicationInfoFlags(long value) { this.value = value; }
        public static ApplicationInfoFlags of(long value) { return new ApplicationInfoFlags(value); }
    }
    public static class NameNotFoundException extends Exception { }
    public abstract ApplicationInfo getApplicationInfo(String name, ApplicationInfoFlags flags)
            throws NameNotFoundException;
}''',
    'android/os/Looper.java': '''package android.os;
public final class Looper {
    private static final Looper MAIN = new Looper();
    private static volatile Thread mainThread;
    public static void enterMainThread() { mainThread = Thread.currentThread(); }
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return Thread.currentThread() == mainThread ? MAIN : null; }
}''',
    'android/os/Handler.java': '''package android.os;
public class Handler { public Handler(Looper looper) { } }''',
    'android/os/UserHandle.java': '''package android.os;
public final class UserHandle {
    public static final UserHandle ALL = new UserHandle(-1);
    public final int id;
    private UserHandle(int id) { this.id = id; }
    public static UserHandle of(int id) { return new UserHandle(id); }
}''',
    'android/util/Log.java': '''package android.util;
public final class Log {
    public static int w(String tag, String message, Throwable failure) { return 0; }
}''',
    'com/android/internal/content/PackageMonitor.java': '''package com.android.internal.content;
import android.content.Context;
import android.os.Handler;
import android.os.UserHandle;
public class PackageMonitor {
    public static PackageMonitor latest;
    public boolean registered;
    public void register(Context context, UserHandle user, Handler handler) {
        if (user != UserHandle.ALL) throw new AssertionError("Other users must be monitored");
        latest = this; registered = true;
    }
    public void unregister() { registered = false; }
    public void onPackageAppLockEnabled(String name) { }
    public void onPackageAppLockDisabled(String name) { }
    public void onPackageModified(String name) { }
    public void onPackageRemoved(String name, int uid) { }
}''',
    'com/android/launcher3/util/SafeCloseable.java': '''package com.android.launcher3.util;
public interface SafeCloseable extends AutoCloseable { void close(); }''',
    'com/android/launcher3/util/Executors.java': '''package com.android.launcher3.util;
import java.util.concurrent.ExecutorService;
public final class Executors {
    public static final ExecutorService MAIN_EXECUTOR =
        java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(() -> { android.os.Looper.enterMainThread(); task.run(); });
            thread.setDaemon(true);
            return thread;
        });
}''',
    'com/android/launcher3/R.java': '''package com.android.launcher3;
public final class R { public static final class string { public static final int drawable_factory_class = 1; } }''',
    'com/android/launcher3/graphics/DrawableFactory.java': '''package com.android.launcher3.graphics;
public class DrawableFactory { }''',
}

PROGRAM = '''import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.os.UserHandle;
import com.android.internal.content.PackageMonitor;
import com.android.launcher3.graphics.DrawableFactory;
import com.android.launcher3.hecker.HeckerAppLockState;
import com.android.launcher3.hecker.HeckerDrawableFactoryProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LauncherNativeAdapterTest {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static final class IconPackFactory extends DrawableFactory {
        static int count;
        static Context receivedContext;
        public IconPackFactory(Context context) {
            check(Looper.myLooper() == Looper.getMainLooper(), "Factory must be created on main thread");
            receivedContext = context; count++;
        }
    }
    static final class Packages extends PackageManager {
        final Map<String, Boolean> states = new HashMap<>();
        int queries;
        boolean denied;
        CountDownLatch started;
        CountDownLatch release;
        @Override public ApplicationInfo getApplicationInfo(String name, ApplicationInfoFlags flags)
                throws NameNotFoundException {
            check(flags.value == GET_APP_LOCK_INFO, "App Lock flag must retain all 64 bits");
            queries++;
            if (denied) throw new SecurityException("Simulated permission failure");
            Boolean enabled = states.get(name);
            if (enabled == null) throw new NameNotFoundException();
            if (started != null) {
                started.countDown();
                try { check(release.await(5, TimeUnit.SECONDS), "Query timed out"); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
            }
            ApplicationInfo info = new ApplicationInfo(); info.isAppLockEnabled = enabled;
            return info;
        }
    }
    static final class TestContext extends Context {
        String factory = "";
        Context app = this;
        final Packages packages = new Packages();
        final Map<Integer, TestContext> users = new HashMap<>();
        @Override public Context getApplicationContext() { return app; }
        @Override public String getString(int id) { check(id == 1, "Unexpected resource"); return factory; }
        @Override public PackageManager getPackageManager() { return packages; }
        @Override public Context createContextAsUser(UserHandle user, int flags) {
            check(flags == 0, "Unexpected context flags"); return users.get(user.id);
        }
    }
    public static void main(String[] args) throws Exception {
        var workers = Executors.newFixedThreadPool(8);
        try {
            TestContext app = new TestContext(); app.factory = IconPackFactory.class.getName();
            TestContext activity = new TestContext(); activity.app = app;
            var provider = new HeckerDrawableFactoryProvider();
            var results = new ArrayList<Future<DrawableFactory>>();
            for (int i = 0; i < 16; i++) results.add(workers.submit(() -> provider.get(activity)));
            DrawableFactory selected = results.get(0).get(5, TimeUnit.SECONDS);
            check(selected instanceof IconPackFactory, "Configured icon pack factory was dropped");
            for (var result : results) check(result.get() == selected, "Factory must be shared");
            check(IconPackFactory.count == 1, "Background calls must not create duplicate factories");
            check(IconPackFactory.receivedContext == app, "Activity context must not be retained");
            for (String invalid : new String[] {"", "missing.IconFactory", "java.lang.String"}) {
                app.factory = invalid;
                check(new HeckerDrawableFactoryProvider().get(app).getClass() == DrawableFactory.class,
                        "Bad overrides must fall back to the stock factory");
            }

            TestContext root = new TestContext(), owner = new TestContext(), other = new TestContext();
            root.users.put(0, owner); root.users.put(10, other);
            owner.packages.states.put("example.app", false);
            other.packages.states.put("example.app", true);
            var state = new HeckerAppLockState(root, Runnable::run);
            var monitor = PackageMonitor.latest;
            check(monitor.registered, "Native package callbacks were not registered");
            AtomicInteger updates = new AtomicInteger();
            Runnable listener = updates::incrementAndGet;
            state.addChangeListener(listener); state.addChangeListener(listener);
            check(!state.hasAppLock(null, 0) && !state.hasAppLock("  ", 0), "Empty packages must be ignored");
            check(!state.hasAppLock("example.app", 0), "Unlocked app was treated as locked");
            check(state.hasAppLock("example.app", 10), "Other user's App Lock state was lost");
            state.hasAppLock("example.app", 0);
            check(owner.packages.queries == 1, "Repeated lookups should use the cache");
            owner.packages.states.put("example.app", true);
            monitor.onPackageAppLockEnabled("example.app");
            check(state.hasAppLock("example.app", 0), "Enabling App Lock must invalidate cached state");
            other.packages.states.put("example.app", false);
            monitor.onPackageAppLockDisabled("example.app");
            check(!state.hasAppLock("example.app", 10), "Disabling App Lock must invalidate cached state");
            check(updates.get() == 2, "Recents must be notified once per change");
            check(!state.hasAppLock("removed.app", 0), "Removed packages should not be locked");
            owner.packages.denied = true;
            check(state.hasAppLock("retry.app", 0), "Failed reads must keep recent-app previews private");
            owner.packages.denied = false; owner.packages.states.put("retry.app", false);
            check(!state.hasAppLock("retry.app", 0), "Temporary failures must be retried, not cached");

            owner.packages.states.put("race.app", true);
            owner.packages.started = new CountDownLatch(1);
            owner.packages.release = new CountDownLatch(1);
            Future<Boolean> inFlight = workers.submit(() -> state.hasAppLock("race.app", 0));
            check(owner.packages.started.await(5, TimeUnit.SECONDS), "Background query did not start");
            owner.packages.states.put("race.app", false);
            Future<?> invalidation = workers.submit(() -> monitor.onPackageAppLockDisabled("race.app"));
            owner.packages.release.countDown();
            check(inFlight.get(5, TimeUnit.SECONDS), "Test must reproduce an old in-flight result");
            invalidation.get(5, TimeUnit.SECONDS);
            owner.packages.started = null;
            check(!state.hasAppLock("race.app", 0), "An in-flight result repopulated a cleared cache");
            state.removeChangeListener(listener);
            int before = updates.get(); monitor.onPackageModified("example.app");
            check(updates.get() == before, "Removed listener was still notified");
            state.close(); check(!monitor.registered, "Package callbacks must unregister on shutdown");
            System.out.println("Icon packs, main-thread singleton, user-specific App Lock, updates, privacy and cache race: PASS");
        } finally {
            workers.shutdownNow();
            com.android.launcher3.util.Executors.MAIN_EXECUTOR.shutdownNow();
        }
    }
}'''


class LauncherNativeAdapterTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which('java'), 'JDK required')
    def test_icon_factory_and_native_applock_behavior(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary)
            paths = []
            for relative, source in STUBS.items():
                path = folder / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)
                paths.append(str(path))
            program = folder / 'LauncherNativeAdapterTest.java'
            program.write_text(PROGRAM)
            paths.append(str(program))
            paths += [str(ROOT / 'launcher/src' / name) for name in
                      ('HeckerDrawableFactoryProvider.java', 'HeckerAppLockState.java')]
            output = folder / 'classes'
            compiled = subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main',
                '-d', str(output), *paths], capture_output=True, text=True, timeout=30)
            self.assertEqual(0, compiled.returncode, compiled.stdout + compiled.stderr)
            result = subprocess.run(['java', '-cp', str(output), 'LauncherNativeAdapterTest'],
                                    capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn('cache race: PASS', result.stdout)


if __name__ == '__main__':
    unittest.main()

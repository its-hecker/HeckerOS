# SPDX-License-Identifier: Apache-2.0
"""Execute native homepage callbacks against the AndroidX list lifecycle contract.

This host probe models list ownership; it does not run an Android Fragment or APK.
The callbacks themselves are extracted unchanged from the prepared native source.
"""
from pathlib import Path
import subprocess


def java_method(source, declaration, *, optional=False):
    start = source.find(declaration)
    if start < 0:
        if optional:
            return ''
        raise ValueError('Missing native callback: ' + declaration)
    brace = source.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def run_lifecycle_probe(source, output):
    """Return the executed probe result; javac failures remain fatal."""
    output = Path(output)
    callbacks = java_method(source, 'public RecyclerView onCreateRecyclerView(')
    callbacks += '\n' + java_method(source, 'public void onViewCreated(', optional=True)
    program = r'''public class SettingsHomeLifecycleProbe {
    static class Bundle {}
    static class LayoutInflater {}
    static class View {
        interface OnAttachStateChangeListener {
            void onViewAttachedToWindow(View view);
            void onViewDetachedFromWindow(View view);
        }
    }
    static class ViewGroup extends View {}
    static class RecyclerView extends View {
        int invalidations, decorations, background;
        void invalidateItemDecorations() { invalidations++; }
        void setVerticalScrollBarEnabled(boolean enabled) {}
        void setHorizontalScrollBarEnabled(boolean enabled) {}
        void setBackgroundColor(int color) { background = color; }
        void addItemDecoration(Object decoration) { decorations++; }
        void addOnAttachStateChangeListener(View.OnAttachStateChangeListener listener) {}
    }
    static class Context { int getColor(int color) { return color; } }
    static class R { static class color { static int hecker_home_background = 17; } }
    static class PreferenceFragmentCompat {
        RecyclerView list;
        int dividerChanges;
        boolean parentViewCreated;
        public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
                Bundle savedInstanceState) { return new RecyclerView(); }
        View onCreateView(Bundle savedInstanceState) {
            // AndroidX assigns mList only after the subclass callback has returned.
            list = onCreateRecyclerView(new LayoutInflater(), new ViewGroup(), savedInstanceState);
            return list;
        }
        public void onViewCreated(View view, Bundle savedInstanceState) {
            if (list == null) throw new AssertionError("parent callback has no list");
            parentViewCreated = true;
        }
        void setDivider(Object divider) {
            list.invalidateItemDecorations();
            if (!parentViewCreated) throw new AssertionError("parent callback must run first");
            dividerChanges++;
        }
        void onDestroyView() { list = null; parentViewCreated = false; }
        Context getContext() { return new Context(); }
    }
    static class TopLevelSettings extends PreferenceFragmentCompat {
        int mPaddingHorizontal;
        void setPaddingHorizontal(int padding) {}
''' + callbacks + r'''
    }
    public static void main(String[] args) {
        TopLevelSettings fragment = new TopLevelSettings();
        for (int cycle = 0; cycle < 3; cycle++) {
            Bundle state = cycle == 0 ? null : new Bundle();
            View view = fragment.onCreateView(state);
            fragment.onViewCreated(view, state);
            if (fragment.list.decorations != 1 || fragment.list.invalidations != 1
                    || fragment.list.background != R.color.hecker_home_background
                    || fragment.dividerChanges != cycle + 1) {
                throw new AssertionError("homepage styling missing or duplicated after recreation");
            }
            fragment.onDestroyView();
        }
        System.out.println("Settings homepage first open, restored view and repeated recreation: PASS");
    }
}
'''
    probe = output / 'SettingsHomeLifecycleProbe.java'
    probe.write_text(program)
    decoration = output / 'com/android/settings/hecker/HeckerHomeDecoration.java'
    decoration.parent.mkdir(parents=True, exist_ok=True)
    decoration.write_text('package com.android.settings.hecker;\n'
                         'public class HeckerHomeDecoration {\n'
                         ' public HeckerHomeDecoration(Object context) {}\n}\n')
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main',
                    '-d', str(output), str(probe), str(decoration)], check=True)
    return subprocess.run(['java', '-cp', str(output), 'SettingsHomeLifecycleProbe'],
                          capture_output=True, text=True)

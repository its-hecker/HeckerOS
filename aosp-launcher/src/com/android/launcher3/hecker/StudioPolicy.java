// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

/** Android-free bounds and formatting shared by the editors and renderers. */
public final class StudioPolicy {
    public static final String[] CLOCKS = {"01_swash.json", "02_bubble.json", "03_sticker.json", "04_puff.json", "05_flourish.json", "06_masthead.json", "07_candy.json", "08_die_cut.json", "09_patchwork.json", "10_pop.json", "11_doodle.json", "12_marker.json", "13_graffiti.json", "14_hello.json", "15_journal.json", "16_chalkboard.json", "17_zine.json", "18_scribble.json", "19_hand.json", "20_space.json", "21_garden.json", "22_kitty.json", "23_party.json", "24_daydream.json", "25_comic.json", "26_wavy.json", "27_love_note.json", "28_dial.json", "29_atelier.json", "30_sun_moon.json", "31_minimal.json", "32_roman.json", "33_station.json", "34_pearl.json", "35_bauhaus.json", "36_ring.json", "37_gilded.json", "38_vogue.json", "39_lumiere.json", "40_editorial.json", "41_bodoni.json", "42_playbill.json", "43_couture.json", "44_ivory.json", "45_stacked.json", "46_inline.json", "47_crystal.json", "48_ember.json", "49_thread.json", "50_layered.json", "51_tilt.json", "52_classic.json", "53_inflate.json", "54_narrow.json", "55_quad.json", "56_glass_quad.json", "57_contrast.json", "58_big_minute.json", "59_weekday.json", "60_calendar.json", "61_staircase.json", "62_layer_cake.json", "63_tilt_duo.json", "64_column.json", "65_outline_mix.json", "66_wide.json", "67_retro.json", "68_sidebar.json", "69_diagonal.json", "70_terminal.json", "71_matrix.json", "72_dots.json", "73_mono.json", "74_readout.json", "75_hud.json", "76_lcd.json", "77_pixel.json", "78_tall.json", "79_spelled.json", "80_fuzzy.json", "81_sentence.json", "82_shout.json", "83_typed.json", "84_note.json", "85_hybrid.json", "86_outline.json"};
    public static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    public static String clock(String style) {
        for (String known : CLOCKS) if (known.equals(style)) return known; return CLOCKS[0];
    }
    public static String badge(int count) { return count <= 0 ? "" : count > 99 ? "99+" : Integer.toString(count); }
    public static float battery(int level, int scale) { return scale <= 0 || level < 0 ? -1 : Math.min(1f, (float) level / scale); }
    public static long nextMinute(long now) { return 60000 - Math.floorMod(now, 60000); }
    public static boolean matches(String query, String label, String packageName) {
        String haystack = (label + " " + packageName).toLowerCase(java.util.Locale.ROOT);
        for (String token : query.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+")) if (!haystack.contains(token)) return false;
        return true;
    }
    private StudioPolicy() {}
}

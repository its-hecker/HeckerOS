// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

/** Conservative music classification. Unknown/video/speech sessions do not open the island. */
final class MusicSessionPolicy {
    static boolean eligible(String pkg, int usage, int content, boolean albumAndArtist) {
        if (pkg == null || (usage != 0 && usage != 1)) return false; // UNKNOWN / MEDIA
        if (pkg.equals("com.google.android.youtube") || pkg.equals("com.netflix.mediaclient")
                || pkg.equals("com.amazon.avod.thirdpartyclient")) return false;
        if (content == 1 || content == 3 || content == 4) return false; // SPEECH / MOVIE / SONIFICATION
        if (content == 2) return true; // AudioAttributes.CONTENT_TYPE_MUSIC
        return albumAndArtist || pkg.equals("com.spotify.music")
                || pkg.equals("com.google.android.apps.youtube.music")
                || pkg.equals("com.apple.android.music") || pkg.equals("com.aspiro.tidal")
                || pkg.equals("deezer.android.app") || pkg.equals("com.maxmpz.audioplayer")
                || pkg.equals("in.krosbits.musicolet");
    }
    private MusicSessionPolicy() { }
}

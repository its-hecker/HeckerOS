package com.android.systemui.bouncer.ui.helper
class BouncerHapticPlayer {
    var failures = 0
    fun playAuthenticationFeedback(authenticationSucceeded: Boolean) {
        if (!authenticationSucceeded) failures++
    }
}

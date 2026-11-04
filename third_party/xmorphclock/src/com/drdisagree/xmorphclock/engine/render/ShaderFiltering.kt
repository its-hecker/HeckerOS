package com.drdisagree.xmorphclock.engine.render

import android.graphics.BitmapShader
import android.os.Build

internal fun BitmapShader.linear(): BitmapShader = apply {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) filterMode = BitmapShader.FILTER_MODE_LINEAR
}

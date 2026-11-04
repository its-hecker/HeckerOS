package com.drdisagree.xmorphclock.engine.render

import android.graphics.BitmapShader

internal class GlassMask(
    val shader: BitmapShader,
    val originX: Float,
    val originY: Float,
    val scale: Float,
    val range: Float
)

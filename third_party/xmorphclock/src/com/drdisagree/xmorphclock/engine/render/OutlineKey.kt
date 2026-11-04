package com.drdisagree.xmorphclock.engine.render

import android.graphics.Paint
import android.graphics.Typeface

internal data class OutlineKey(
    val text: String,
    val typeface: Typeface,
    val align: Paint.Align,
    val letterSpacing: Float
)

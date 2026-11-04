package com.drdisagree.xmorphclock.engine.render

import com.drdisagree.xmorphclock.domain.model.FaceTextEffect

internal class Placement(
    val left: Float,
    val top: Float,
    val size: Float,
    val alpha: Float,
    val rotation: Float,
    val capRatio: Float,
    val advances: FloatArray,
    val widthAxis: Float,
    val heightAxis: Float,
    val tracking: Float,
    val weight: Float,
    val color: Int,
    val lineHeight: Float,
    val stretch: Float,
    val stretchX: Float,
    val effect: FaceTextEffect,
    val hidden: Boolean = false
)

package com.drdisagree.xmorphclock.domain.model

data class FacePlacement(
    val x: Float = 0f,
    val y: Float = 0f,
    val anchor: FaceAlign = FaceAlign.START,
    val hidden: Boolean = false
)

package com.drdisagree.xmorphclock.domain.model

data class SnapResult(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val guideX: Float? = null,
    val guideY: Float? = null,
    val anchorX: FaceAlign? = null
)

package com.drdisagree.xmorphclock.domain.model

data class PresetFilePreview(
    val entries: List<PresetFileEntry> = emptyList(),
    val error: PresetFileError? = null
)

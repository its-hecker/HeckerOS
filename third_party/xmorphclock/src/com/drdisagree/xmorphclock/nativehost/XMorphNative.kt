/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: GPL-3.0-or-later */
package com.drdisagree.xmorphclock.nativehost

import android.content.Context
import com.drdisagree.xmorphclock.data.mapper.ClockFaceJsonMapper
import com.drdisagree.xmorphclock.domain.model.ClockFace

/** Immutable packaged clock catalog shared by the native picker and SystemUI. */
object XMorphNative {
    const val CLOCK_STYLE = 6
    const val KEY = "hecker_xmorph_clock_preset"
    const val COUNT = 86
    data class Entry(val id: String, val name: String, val category: String, val face: ClockFace)
    @Volatile private var cached: List<Entry>? = null

    @JvmStatic fun entries(context: Context): List<Entry> = cached ?: synchronized(this) {
        cached ?: load(context).also { cached = it }
    }

    @JvmStatic fun selected(context: Context, id: String?): Entry =
        entries(context).firstOrNull { it.id == id } ?: entries(context).first()

    private fun load(context: Context): List<Entry> {
        val names = context.assets.list("clock_faces").orEmpty().filter { it.endsWith(".json") }.sorted()
        check(names.size == COUNT) { "Incomplete packaged XMorphClock catalog" }
        return names.map { name ->
            val json = context.assets.open("clock_faces/$name").bufferedReader().use { it.readText() }
            check(json.length <= 65536) { "Oversized packaged clock preset" }
            val face = requireNotNull(ClockFaceJsonMapper.fromJson(json)) { "Invalid clock preset: $name" }
            Entry(name, face.name, face.category, face)
        }
    }
}

package com.drdisagree.xmorphclock.engine.render

import android.content.res.Resources
import com.drdisagree.xmorphclock.R
import java.util.Locale

internal data class WordPhrases(
    val language: String,
    val oclock: String,
    val past: String,
    val to: String,
    val quarterPast: String,
    val halfPast: String,
    val quarterTo: String,
    val leadPast: String,
    val leadTo: String,
    val leadQuarterPast: String,
    val leadHalfPast: String,
    val leadQuarterTo: String,
    val hundred: String
) {
    fun numbers(locale: Locale): Locale = if (locale.language == language) locale else Locale.ENGLISH

    companion object {
        fun from(resources: Resources): WordPhrases = WordPhrases(
            language = resources.getString(R.string.xmorph_words_language),
            oclock = resources.getString(R.string.xmorph_fuzzy_oclock),
            past = resources.getString(R.string.xmorph_fuzzy_past),
            to = resources.getString(R.string.xmorph_fuzzy_to),
            quarterPast = resources.getString(R.string.xmorph_fuzzy_quarter_past),
            halfPast = resources.getString(R.string.xmorph_fuzzy_half_past),
            quarterTo = resources.getString(R.string.xmorph_fuzzy_quarter_to),
            leadPast = resources.getString(R.string.xmorph_fuzzy_lead_past),
            leadTo = resources.getString(R.string.xmorph_fuzzy_lead_to),
            leadQuarterPast = resources.getString(R.string.xmorph_fuzzy_lead_quarter_past),
            leadHalfPast = resources.getString(R.string.xmorph_fuzzy_lead_half_past),
            leadQuarterTo = resources.getString(R.string.xmorph_fuzzy_lead_quarter_to),
            hundred = resources.getString(R.string.xmorph_words_hundred)
        )
    }
}

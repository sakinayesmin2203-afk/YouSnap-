package com.example.ui.utils

import java.util.Locale

object FormatUtils {
    fun formatCount(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", count / 1_000_000f).replace(".0", "")
            count >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", count / 1_000f).replace(".0", "")
            else -> count.toString()
        }
    }
}

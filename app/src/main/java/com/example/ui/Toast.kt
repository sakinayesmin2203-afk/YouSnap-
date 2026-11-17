package com.example.ui

import android.content.Context

class SilentToast {
    fun show() {}
    fun cancel() {}
    fun setText(text: CharSequence) {}
    fun setDuration(duration: Int) {}
}

object Toast {
    fun makeText(context: Context, message: CharSequence, duration: Int): SilentToast {
        return SilentToast()
    }

    fun makeText(context: Context, resId: Int, duration: Int): SilentToast {
        return SilentToast()
    }

    const val LENGTH_SHORT = 0
    const val LENGTH_LONG = 1
}

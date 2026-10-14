package com.example.ui

object Toast {
    fun makeText(c: android.content.Context?, t: CharSequence?, d: Int): FakeToast = FakeToast
    fun makeText(c: android.content.Context?, resId: Int, d: Int): FakeToast = FakeToast
    const val LENGTH_SHORT = 0
    const val LENGTH_LONG = 1
}

object FakeToast {
    fun show() {}
}

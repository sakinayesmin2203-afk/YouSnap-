package com.example.ui

import android.content.Context
import android.widget.Toast

object Toast {
    fun makeText(context: Context, message: String, duration: Int): android.widget.Toast {
        return android.widget.Toast.makeText(context, message, duration)
    }
    const val LENGTH_SHORT = android.widget.Toast.LENGTH_SHORT
    const val LENGTH_LONG = android.widget.Toast.LENGTH_LONG
}

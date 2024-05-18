package com.example.myapplication

import android.net.Uri

data class Message(
    val imageUri: Uri? = null,
    val textContent: String? = null,
    val isSummary: Boolean = false,
    val viewType: Int
)
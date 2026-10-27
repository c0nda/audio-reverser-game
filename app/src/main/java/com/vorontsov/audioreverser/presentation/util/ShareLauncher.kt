package com.vorontsov.audioreverser.presentation.util

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.vorontsov.audioreverser.R

fun Context.shareAudio(uriString: String, mimeType: String, title: String) {
    val uri = uriString.toUri()
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, getString(R.string.share_chooser_title))
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ContextCompat.startActivity(this, chooser, null)
}

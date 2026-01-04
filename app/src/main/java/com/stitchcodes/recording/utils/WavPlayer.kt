package com.stitchcodes.recording.utils

import android.content.Intent
import android.net.Uri
import com.stitchcodes.recording.ContextHolder

object WavPlayer {
    fun playWithSystemPlayer(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/wav")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ContextHolder.appContext().startActivity(intent)
    }
}
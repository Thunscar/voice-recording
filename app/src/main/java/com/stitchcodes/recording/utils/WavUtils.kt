package com.stitchcodes.recording.utils

import android.media.MediaMetadataRetriever
import android.net.Uri
import com.stitchcodes.recording.ContextHolder

object WavUtils {
    fun getWavDurationMs(uri: Uri): Long {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(ContextHolder.appContext(), uri)
        val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        mmr.release()
        return duration?.toLong() ?: 0L
    }
}
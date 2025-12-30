package com.stitchcodes.recording

import android.app.Application
import android.util.Log
import com.stitchcodes.recording.vad.webrtc.VadNative

class RecordingApp : Application() {

    companion object {
        private const val TAG = "RecordingApp"
    }

    override fun onCreate() {
        ContextHolder.initContext(this)
        Log.d(TAG, "init vad native...")
        VadNative.getInstance().init(3)
        super.onCreate()
    }

}
package com.stitchcodes.recording

import android.app.Application

class RecordingApp : Application() {

    companion object {
        private const val TAG = "RecordingApp"
    }

    override fun onCreate() {
        ContextHolder.initContext(this)
        super.onCreate()
    }

}
package com.stitchcodes.recording

import android.content.Context

object ContextHolder {

    private lateinit var appContext: Context

    fun appContext(): Context {
        return appContext
    }

    fun initContext(context: Context) {
        appContext = context
    }

}
package com.stitchcodes.recording.utils

import android.app.ActivityManager
import android.content.Context

object ServiceUtils {
    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = manager.getRunningServices(Int.MAX_VALUE)

        for (serviceInfo in services) {
            if (serviceInfo.service.className == serviceClass.name) {
                return true
            }
        }
        return false
    }
}
package com.stitchcodes.recording.utils

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat

object PermsUtils {

    private const val RECORD_AUDIO_REQUEST_CODE = 1001

    /**
     * 检查是否有权限
     */
    fun hasPerms(context: Context, perms: String): Boolean {
        return ContextCompat.checkSelfPermission(context, perms) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 初始化程序权限
     */
    fun requestPerms(context: Context, perms: Array<String>) {
        try {
            (context as Activity).requestPermissions(perms, RECORD_AUDIO_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(context, "初始化权限失败", Toast.LENGTH_SHORT).show()
        }
    }

}
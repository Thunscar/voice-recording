package com.stitchcodes.recording.utils

import android.app.Activity
import android.widget.Toast
import androidx.activity.result.*
import androidx.activity.result.contract.ActivityResultContracts

object PermsLauncher {

    private lateinit var launcher: ActivityResultLauncher<String>

    fun init(activity: Activity, caller: ActivityResultCaller) {
        launcher = caller.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(activity, "权限获取失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun request(permission: String) {
        launcher.launch(permission)
    }

}
package com.stitchcodes.recording

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import com.stitchcodes.recording.utils.PermsLauncher
import com.stitchcodes.recording.ui.view.RecordingScreen
import com.stitchcodes.recording.ui.theme.RecordingTheme
import com.stitchcodes.recording.utils.FoldPickerLauncher

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        batteryOptimizations()
        PermsLauncher.init(this, this)
        FoldPickerLauncher.init(this, this)
        //检查通知权限
        checkNotificationPermission()
        enableEdgeToEdge()
        setContent {
            RecordingTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RecordingScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun batteryOptimizations() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val isIgnore = powerManager.isIgnoringBatteryOptimizations(packageName)
            if (!isIgnore) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermsLauncher.request(POST_NOTIFICATIONS)
        } else {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                Toast.makeText(this, "在系统设置中开启通知", Toast.LENGTH_SHORT).show()
            }
        }
    }

}
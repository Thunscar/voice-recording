package com.stitchcodes.recording.service

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.stitchcodes.recording.R

class RecordingService : Service() {

    companion object {
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "RecordingChannel"
        private const val CHANNEL_NAME = "RecordingChannel"
        private const val NOTIFICATION_ID = 191919
    }

    private lateinit var voiceHandler: VoiceRecordHandler

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("人声录制").setContentText("人声自动录制中...")
            .setSmallIcon(R.drawable.ic_launcher_foreground_han).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "start recording service")
        voiceHandler = VoiceRecordHandler()
        voiceHandler.init(this)
        voiceHandler.start()
        return START_STICKY
    }

    //创建服务渠道
    private fun createNotificationChannel() {
        try {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            val manager = this.getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(channel)
            Log.d(TAG, "Recording Service Start Success")
        } catch (e: Exception) {
            Log.e(TAG, "Recording Service Start Failed", e)
        }
    }

    override fun onDestroy() {
        if (::voiceHandler.isInitialized) {
            voiceHandler.stop()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
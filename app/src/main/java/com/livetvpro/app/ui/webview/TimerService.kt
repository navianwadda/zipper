package com.livetvpro.app.ui.webview

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.livetvpro.app.R

class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var durationSeconds = 0L
    private var startTimeMs = 0L

    private val tickRunnable = object : Runnable {
        override fun run() {
            val elapsed = (System.currentTimeMillis() - startTimeMs) / 1000L
            val remaining = durationSeconds - elapsed
            if (remaining <= 0) {
                updateNotification(0)
                sendBroadcast(Intent(ACTION_TIMER_DONE).setPackage(packageName))
                stopSelf()
            } else {
                updateNotification(remaining)
                handler.postDelayed(this, 1000L)
            }
        }
    }

    companion object {
        const val ACTION_TIMER_DONE   = "com.livetvpro.app.AD_TIMER_DONE"
        const val EXTRA_DURATION      = "extra_duration"
        private const val CHANNEL_ID  = "ad_timer_channel"
        private const val NOTIF_ID    = 9001

        fun start(context: Context, durationSeconds: Long) {
            val intent = Intent(context, TimerService::class.java)
                .putExtra(EXTRA_DURATION, durationSeconds)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Starting…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        durationSeconds = intent?.getLongExtra(EXTRA_DURATION, 30L) ?: 30L
        startTimeMs = System.currentTimeMillis()
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tickRunnable)
    }

    private fun updateNotification(remainingSeconds: Long) {
        val text = if (remainingSeconds > 0) "Please wait ${remainingSeconds}s…"
                   else "Thank you for your support!"
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle("Ad Timer")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tapIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ad Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Counts down the ad timer" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

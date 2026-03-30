package com.timely.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.Calendar
import java.util.Locale

/**
 * Foreground service that runs time announcements in the background.
 * Uses a Handler + Runnable loop to fire announcements at the set interval.
 * TextToSpeech is initialised once on creation and reused for every announcement.
 */
class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private lateinit var settings: SettingsRepository

    companion object {
        const val CHANNEL_ID = "timely_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.timely.app.ACTION_STOP"
        private const val TAG = "TimerService"
    }

    // -------------------------------------------------------------------------
    // Timer loop – fires every intervalMinutes, then reschedules itself
    // -------------------------------------------------------------------------
    private val timerRunnable = object : Runnable {
        override fun run() {
            checkAndAnnounce()
            val delayMs = settings.intervalMinutes * 60_000L
            handler.postDelayed(this, delayMs)
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------
    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        createNotificationChannel()
        initTts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutDown()
            stopSelf()
            return START_NOT_STICKY
        }

        settings.isRunning = true
        
        val notification = buildNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Remove any stale callbacks, then kick off the loop immediately
        handler.removeCallbacks(timerRunnable)
        handler.post(timerRunnable)

        return START_STICKY
    }

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------
    private fun shutDown() {
        handler.removeCallbacks(timerRunnable)
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
        settings.isRunning = false
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.getDefault())
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                if (!isTtsReady) {
                    // Fall back to English
                    val fallback = tts?.setLanguage(Locale.ENGLISH)
                    isTtsReady = fallback != TextToSpeech.LANG_MISSING_DATA &&
                            fallback != TextToSpeech.LANG_NOT_SUPPORTED
                }
            } else {
                Log.e(TAG, "TTS initialisation failed with status: $status")
            }
        }
    }

    private fun checkAndAnnounce() {
        val now = Calendar.getInstance()

        // Convert Android's Calendar day (Sun=1…Sat=7) → our 1–7 (Mon=1…Sun=7)
        val dayIndex = when (now.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY    -> 1
            Calendar.TUESDAY   -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY  -> 4
            Calendar.FRIDAY    -> 5
            Calendar.SATURDAY  -> 6
            Calendar.SUNDAY    -> 7
            else               -> return
        }

        val currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMins   = settings.startHour * 60 + settings.startMinute
        val endMins     = settings.endHour   * 60 + settings.endMinute

        val isSelectedDay  = dayIndex in settings.selectedDays
        val isInTimeRange  = currentMins in startMins..endMins

        if (isSelectedDay && isInTimeRange && isTtsReady) {
            speakTime(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        }
    }

    private fun speakTime(hour: Int, minute: Int) {
        val text = buildTimePhrase(hour, minute)
        Log.d(TAG, "Speaking: $text")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TIME_ANNOUNCEMENT")
    }

    private fun buildTimePhrase(hour: Int, minute: Int): String {
        val period = if (hour < 12) "A M" else "P M"
        val h = when {
            hour == 0  -> 12
            hour > 12  -> hour - 12
            else       -> hour
        }
        return when {
            minute == 0       -> "The time is $h o'clock $period"
            minute < 10       -> "The time is $h oh $minute $period"
            else              -> "The time is $h $minute $period"
        }
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Timely – background timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Timely running so it can announce the time"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TimerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Timely is running")
            .setContentText("Announcing time every ${settings.intervalMinutes} min")
            .setSmallIcon(R.drawable.ic_time_notification)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

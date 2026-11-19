package ru.pavellysov.carplaywelcome

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import java.time.LocalTime

const val KEY_ENABLED = "enabled"
const val KEY_RECONNECT_MINUTES = "reconnect_minutes"
const val KEY_LOG = "log"

private const val CHANNEL_ID = "monitoring"
private const val NOTIFICATION_ID = 1
private const val ZLINK_PACKAGE = "com.zjinnova.zlink"

fun preferences(context: Context) = context.getSharedPreferences("welcome", Context.MODE_PRIVATE)

class WelcomeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private var wasConnected = false
    private var disconnectedAt = 0L
    private var pending: Sound? = null
    private var phoneIdle = true
    private var telephonyCallback: TelephonyCallback? = null

    private val checkConnection = object : Runnable {
        override fun run() {
            checkZlink()
            handler.postDelayed(this, 5_000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())
        if (intent?.action == ACTION_TEST) {
            play(greetingFor(LocalTime.now()))
            return START_NOT_STICKY
        }
        handler.removeCallbacks(checkConnection)
        handler.post(checkConnection)
        registerPhoneListener()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        telephonyCallback?.let { getSystemService(TelephonyManager::class.java).unregisterTelephonyCallback(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkZlink() {
        if (!hasUsageAccess()) {
            log("Нет Usage Access — мониторинг ZLink выключен")
            return
        }
        val connected = isZlinkForeground()
        val now = System.currentTimeMillis()
        if (connected && !wasConnected) {
            val reconnectAfter = preferences(this).getInt(KEY_RECONNECT_MINUTES, 2) * 60_000L
            val sound = if (disconnectedAt > 0 && now - disconnectedAt >= reconnectAfter) Sound.RESTORED else greetingFor(LocalTime.now())
            wasConnected = true
            disconnectedAt = 0
            log("ZLink активен: ${sound.rawName}")
            playWhenCallEnds(sound)
        } else if (!connected && wasConnected) {
            wasConnected = false
            disconnectedAt = now
            log("ZLink больше не активен")
        }
    }

    private fun hasUsageAccess(): Boolean {
        return getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun isZlinkForeground(): Boolean {
        val manager = getSystemService(UsageStatsManager::class.java)
        val events = manager.queryEvents(System.currentTimeMillis() - 20_000, System.currentTimeMillis())
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastPackage = event.packageName
            }
        }
        return lastPackage == ZLINK_PACKAGE
    }

    private fun registerPhoneListener() {
        if (telephonyCallback != null || checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                phoneIdle = state == TelephonyManager.CALL_STATE_IDLE
                if (phoneIdle) pending?.let { sound -> pending = null; play(sound) }
            }
        }
        getSystemService(TelephonyManager::class.java).registerTelephonyCallback(mainExecutor, telephonyCallback!!)
    }

    private fun playWhenCallEnds(sound: Sound) {
        if (phoneIdle) play(sound) else {
            pending = sound
            log("Фраза ждёт завершения звонка")
        }
    }

    private fun play(sound: Sound) {
        val resource = resources.getIdentifier(sound.rawName, "raw", packageName)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build())
            .build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            log("Не удалось получить audio focus")
            return
        }
        MediaPlayer.create(this, resource).apply {
            setOnCompletionListener { player ->
                player.release()
                audioManager.abandonAudioFocusRequest(request)
                log("Воспроизведено: ${sound.rawName}")
            }
            start()
        }
    }

    private fun log(message: String) {
        val entry = "${java.time.LocalDateTime.now()}: $message"
        val entries = (preferences(this).getString(KEY_LOG, "")!!.lines() + entry).takeLast(20)
        preferences(this).edit().putString(KEY_LOG, entries.joinToString("\n")).apply()
    }

    private fun notification() = android.app.Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("CarPlay Welcome включён")
        .setContentText("Отслеживается ZLink")
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .build()

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "CarPlay Welcome", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val ACTION_TEST = "ru.pavellysov.carplaywelcome.TEST"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WelcomeService::class.java))
        }
    }
}

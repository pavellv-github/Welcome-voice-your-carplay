package ru.pavellysov.carplaywelcome

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import java.time.LocalDateTime
import java.time.LocalTime

const val KEY_BLUETOOTH_MODE = "bluetooth_mode"
const val KEY_BLUETOOTH_ADDRESS = "bluetooth_address"
const val KEY_CONFIGURED = "configured"
const val KEY_DELAY_MINUTES = "delay_minutes"
const val KEY_LOG = "log"
const val KEY_BLUETOOTH_GREETING_PLAYED = "bluetooth_greeting_played"
const val KEY_LAST_DIAGNOSTIC = "last_diagnostic"
const val KEY_LAST_DIAGNOSTIC_AT = "last_diagnostic_at"

private const val CHANNEL_ID = "monitoring"
private const val NOTIFICATION_ID = 1

fun preferences(context: Context) = context.getSharedPreferences("welcome", Context.MODE_PRIVATE)

class WelcomeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private var pending: Sound? = null
    private var pendingFromBluetooth = false
    private var phoneIdle = true
    private var telephonyCallback: TelephonyCallback? = null
    @Suppress("DEPRECATION") private var phoneStateListener: PhoneStateListener? = null
    private var receiverRegistered = false
    private var stopAfterPlayback = false

    private var scheduledSound: Sound? = null
    private var scheduledFromBluetooth = false
    private val playAfterDelay = Runnable {
        scheduledSound?.let { playWhenCallEnds(it, scheduledFromBluetooth) }
        scheduledSound = null
        scheduledFromBluetooth = false
    }
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            if (device.address != preferences(this@WelcomeService).getString(KEY_BLUETOOTH_ADDRESS, null)) return
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    handler.removeCallbacks(playAfterDelay)
                    scheduledSound = if (preferences(this@WelcomeService).getBoolean(KEY_BLUETOOTH_GREETING_PLAYED, false)) {
                        Sound.RESTORED
                    } else {
                        greetingFor(LocalTime.now())
                    }
                    scheduledFromBluetooth = true
                    handler.postDelayed(playAfterDelay, delayMillis())
                    log("Подключено Bluetooth-устройство: ${device.address}; ожидание ${delayMinutes()} мин. (${scheduledSound!!.rawName})")
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    handler.removeCallbacks(playAfterDelay)
                    scheduledSound = null
                    scheduledFromBluetooth = false
                    log("Отключено Bluetooth-устройство: ${device.address}")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())
        registerPhoneListener()
        when (intent?.action) {
            ACTION_TEST -> playWhenCallEnds(greetingFor(LocalTime.now()))
            ACTION_DIAGNOSTIC -> intent.getStringExtra(EXTRA_DIAGNOSTIC)?.let { name ->
                DiagnosticEvent.values().firstOrNull { it.name == name }?.let(::playDiagnostic)
            }
            ACTION_BOOT -> startForConfiguredMode(fromBoot = true)
            else -> startForConfiguredMode(fromBoot = false)
        }
        return if (intent?.action == ACTION_TEST || intent?.action == ACTION_DIAGNOSTIC) START_NOT_STICKY
        else if (preferences(this).getBoolean(KEY_BLUETOOTH_MODE, true)) START_STICKY
        else START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterBluetoothReceiver()
        val manager = getSystemService(TelephonyManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) telephonyCallback?.let(manager::unregisterTelephonyCallback)
        else phoneStateListener?.let { manager.listen(it, PhoneStateListener.LISTEN_NONE) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForConfiguredMode(fromBoot: Boolean) {
        handler.removeCallbacks(playAfterDelay)
        scheduledSound = null
        scheduledFromBluetooth = false
        stopAfterPlayback = false
        if (preferences(this).getBoolean(KEY_BLUETOOTH_MODE, true)) {
            startBluetoothMonitoring()
        } else {
            unregisterBluetoothReceiver()
            if (fromBoot) {
                stopAfterPlayback = true
                scheduledSound = greetingFor(LocalTime.now())
                scheduledFromBluetooth = false
                handler.postDelayed(playAfterDelay, delayMillis())
                log("После загрузки магнитолы: ожидание ${delayMinutes()} мин.")
            } else {
                log("Выбран запуск после включения магнитолы")
                stopSelf()
            }
        }
    }

    private fun startBluetoothMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            log("Нет разрешения Bluetooth")
            return
        }
        if (preferences(this).getString(KEY_BLUETOOTH_ADDRESS, null) == null) {
            log("Не выбрано Bluetooth-устройство")
            return
        }
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(bluetoothReceiver, filter)
            receiverRegistered = true
        }
        log("Ожидание Bluetooth-устройства")
    }

    private fun unregisterBluetoothReceiver() {
        if (receiverRegistered) {
            unregisterReceiver(bluetoothReceiver)
            receiverRegistered = false
        }
    }

    private fun delayMinutes() = preferences(this).getInt(KEY_DELAY_MINUTES, 1)
    private fun delayMillis() = delayMinutes() * 60_000L

    private fun registerPhoneListener() {
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(TelephonyManager::class.java)
        phoneIdle = manager.callState == TelephonyManager.CALL_STATE_IDLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (telephonyCallback != null) return
            telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = onPhoneStateChanged(state)
            }
            manager.registerTelephonyCallback(mainExecutor, telephonyCallback!!)
        } else {
            if (phoneStateListener != null) return
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = onPhoneStateChanged(state)
            }
            phoneStateListener = listener
            @Suppress("DEPRECATION") manager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun onPhoneStateChanged(state: Int) {
        phoneIdle = state == TelephonyManager.CALL_STATE_IDLE
        if (phoneIdle) pending?.let { sound ->
            val fromBluetooth = pendingFromBluetooth
            pending = null
            pendingFromBluetooth = false
            play(sound, fromBluetooth)
        }
    }

    private fun playWhenCallEnds(sound: Sound, fromBluetooth: Boolean = false) {
        if (phoneIdle) play(sound, fromBluetooth) else {
            pending = sound
            pendingFromBluetooth = fromBluetooth
            log("Фраза ждёт завершения звонка")
        }
    }

    private fun playDiagnostic(event: DiagnosticEvent) {
        val prefs = preferences(this)
        val now = System.currentTimeMillis()
        if (event.severity != Severity.CRITICAL &&
            prefs.getString(KEY_LAST_DIAGNOSTIC, null) == event.name &&
            now - prefs.getLong(KEY_LAST_DIAGNOSTIC_AT, 0) < DIAGNOSTIC_REPEAT_DELAY_MS
        ) {
            log("Повтор диагностического события пропущен: ${event.name}")
            return
        }
        prefs.edit().putString(KEY_LAST_DIAGNOSTIC, event.name).putLong(KEY_LAST_DIAGNOSTIC_AT, now).apply()
        log("Диагностическое событие: ${event.name}")
        playWhenCallEnds(event.sound)
    }

    private fun play(sound: Sound, fromBluetooth: Boolean) {
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
                if (fromBluetooth && sound != Sound.RESTORED) preferences(this@WelcomeService).edit().putBoolean(KEY_BLUETOOTH_GREETING_PLAYED, true).apply()
                log("Воспроизведено: ${sound.rawName}")
                if (stopAfterPlayback) stopSelf()
            }
            start()
        }
    }

    private fun log(message: String) {
        val entry = "${LocalDateTime.now()}: $message"
        val entries = (preferences(this).getString(KEY_LOG, "")!!.lines().filter { it.isNotBlank() } + entry).takeLast(20)
        preferences(this).edit().putString(KEY_LOG, entries.joinToString("\n")).apply()
    }

    private fun notification(): Notification = Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("AI イゴール включён")
        .setContentText(if (preferences(this).getBoolean(KEY_BLUETOOTH_MODE, true)) "Ожидание Bluetooth" else "Ожидание запуска магнитолы")
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .build()

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "AI イゴール", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val ACTION_BOOT = "ru.pavellysov.carplaywelcome.BOOT"
        const val ACTION_TEST = "ru.pavellysov.carplaywelcome.TEST"
        const val ACTION_DIAGNOSTIC = "ru.pavellysov.carplaywelcome.DIAGNOSTIC"
        const val EXTRA_DIAGNOSTIC = "diagnostic"
        private const val DIAGNOSTIC_REPEAT_DELAY_MS = 10 * 60_000L

        fun start(context: Context, action: String? = null) {
            val intent = Intent(context, WelcomeService::class.java).also { it.action = action }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun startDiagnostic(context: Context, event: DiagnosticEvent) {
            val intent = Intent(context, WelcomeService::class.java)
                .setAction(ACTION_DIAGNOSTIC)
                .putExtra(EXTRA_DIAGNOSTIC, event.name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}

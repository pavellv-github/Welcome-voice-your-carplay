package ru.pavellysov.carplaywelcome

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var logView: TextView
    private lateinit var deviceSpinner: Spinner
    private lateinit var bluetoothCard: LinearLayout
    private lateinit var modeSubtitle: TextView
    private var devices: List<BluetoothDevice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = preferences(this)
        prefs.edit().putBoolean(KEY_CONFIGURED, true).apply()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
            setPadding(dp(16), dp(18), dp(16), dp(24))
        }
        val scroll = ScrollView(this).apply { addView(root) }

        root.addView(label("AI イゴール", 28, Color.WHITE, true))
        root.addView(label("Голосовой ассистент магнитолы", 14, MUTED), margin(top = 2, bottom = 18))

        val mode = Switch(this).apply {
            isChecked = prefs.getBoolean(KEY_BLUETOOTH_MODE, true)
            setTextColor(BLACK)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        }
        modeSubtitle = label("", 13, Color.rgb(77, 38, 8))
        val delay = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, (1..5).map { "$it мин." })
            setSelection(prefs.getInt(KEY_DELAY_MINUTES, 1) - 1)
            onItemSelectedListener = simpleSelection { position -> prefs.edit().putInt(KEY_DELAY_MINUTES, position + 1).apply() }
        }
        root.addView(card(ORANGE) {
            addView(label("СЦЕНАРИЙ ЗАПУСКА", 11, Color.rgb(92, 43, 8), true))
            addView(mode, margin(top = 8))
            addView(modeSubtitle, margin(top = 2, bottom = 12))
            addView(label("ЗАДЕРЖКА ПЕРЕД ПРИВЕТСТВИЕМ", 11, Color.rgb(92, 43, 8), true))
            addView(delay, margin(top = 4))
        }, margin(bottom = 10))

        val bluetoothPermission = action("Разрешить Bluetooth", YELLOW, BLACK) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), BLUETOOTH_REQUEST)
            else loadDevices()
        }
        deviceSpinner = Spinner(this).apply {
            onItemSelectedListener = simpleSelection { position ->
                devices.getOrNull(position)?.let {
                    prefs.edit().putString(KEY_BLUETOOTH_ADDRESS, it.address).apply()
                    WelcomeService.start(this@MainActivity)
                }
            }
        }
        bluetoothCard = card(YELLOW) {
            addView(label("BLUETOOTH", 11, Color.rgb(91, 73, 0), true))
            addView(label("Выберите телефон для отслеживания подключения", 15, BLACK, true), margin(top = 7, bottom = 8))
            addView(bluetoothPermission)
            addView(deviceSpinner, margin(top = 8))
            addView(action("Обновить список устройств", Color.TRANSPARENT, BLACK) { loadDevices() }, margin(top = 4))
        }
        root.addView(bluetoothCard, margin(bottom = 10))

        root.addView(card(CYAN) {
            addView(label("АУДИО-СЦЕНАРИИ", 11, Color.rgb(44, 83, 86), true))
            addView(label("Утро • день • вечер • ночь", 18, BLACK, true), margin(top = 7))
            addView(label("Приветствия, Bluetooth и 37 диагностических сообщений", 13, Color.rgb(30, 63, 67)), margin(top = 4))
        }, margin(bottom = 10))

        root.addView(action("▶  Проверить фразу для текущего времени", GREEN, Color.WHITE) {
            WelcomeService.start(this@MainActivity, WelcomeService.ACTION_TEST)
        }, margin(bottom = 10))
        root.addView(action("Проверить предупреждение о перегреве", DARK, Color.WHITE) {
            WelcomeService.startDiagnostic(this@MainActivity, DiagnosticEvent.ENGINE_OVERHEAT)
        }, margin(bottom = 10))
        val diagnosticScenario = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                DiagnosticEvent.values().map { "[${it.severity}] ${it.sound.rawName}" }
            )
        }
        root.addView(card(CYAN) {
            addView(label("ТЕСТ ДИАГНОСТИКИ", 11, Color.rgb(44, 83, 86), true))
            addView(label("Выберите и прослушайте любую локальную дорожку", 15, BLACK, true), margin(top = 7, bottom = 8))
            addView(diagnosticScenario)
            addView(action("▶  Прослушать выбранный сценарий", Color.TRANSPARENT, BLACK) {
                WelcomeService.startDiagnostic(this@MainActivity, DiagnosticEvent.values()[diagnosticScenario.selectedItemPosition])
            }, margin(top = 4))
        }, margin(bottom = 10))
        root.addView(action("Разрешить ожидание звонка и уведомления", DARK, Color.WHITE) {
            val permissions = mutableListOf(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions += Manifest.permission.POST_NOTIFICATIONS
            requestPermissions(permissions.toTypedArray(), PHONE_REQUEST)
        }, margin(bottom = 10))

        logView = label("Пока нет событий", 13, Color.WHITE).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(DARK)
        }
        root.addView(label("ЖУРНАЛ", 11, MUTED, true), margin(top = 4, bottom = 5))
        root.addView(logView)

        fun refreshMode() {
            val bluetooth = mode.isChecked
            mode.text = if (bluetooth) "Запуск по Bluetooth" else "После включения магнитолы"
            modeSubtitle.text = if (bluetooth) "Ожидание подключения выбранного телефона" else "Приветствие после загрузки Android"
            bluetoothCard.visibility = if (bluetooth) View.VISIBLE else View.GONE
            prefs.edit().putBoolean(KEY_BLUETOOTH_MODE, bluetooth).apply()
            WelcomeService.start(this@MainActivity)
        }
        mode.setOnCheckedChangeListener { _, _ -> refreshMode() }
        setContentView(scroll)
        refreshMode()
        loadDevices()
    }

    override fun onResume() {
        super.onResume()
        logView.text = preferences(this).getString(KEY_LOG, "Пока нет событий")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BLUETOOTH_REQUEST) loadDevices()
    }

    private fun loadDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Разрешите Bluetooth"))
            return
        }
        devices = BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.sortedBy { it.name ?: it.address }.orEmpty()
        val labels = devices.map { "${it.name ?: "Без имени"} (${it.address})" }.ifEmpty { listOf("Нет сопряжённых устройств") }
        deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val selected = preferences(this).getString(KEY_BLUETOOTH_ADDRESS, null)
        deviceSpinner.setSelection(devices.indexOfFirst { it.address == selected }.coerceAtLeast(0))
    }

    private fun action(text: String, color: Int, textColor: Int, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(textColor)
        textSize = 15f
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        background = rounded(color)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { onClick() }
    }

    private fun card(color: Int, content: LinearLayout.() -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(color)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        content()
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(22).toFloat()
    }

    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { setMargins(0, dp(top), 0, dp(bottom)) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun simpleSelection(onSelected: (Int) -> Unit) = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = onSelected(position)
    }

    companion object {
        private const val BLACK = 0xFF090909.toInt()
        private const val DARK = 0xFF202020.toInt()
        private const val ORANGE = 0xFFFF5A0A.toInt()
        private const val YELLOW = 0xFFF8BA00.toInt()
        private const val CYAN = 0xFFB9E4E4.toInt()
        private const val GREEN = 0xFF005239.toInt()
        private const val MUTED = 0xFFB5B1AD.toInt()
        private const val BLUETOOTH_REQUEST = 1
        private const val PHONE_REQUEST = 2
    }
}

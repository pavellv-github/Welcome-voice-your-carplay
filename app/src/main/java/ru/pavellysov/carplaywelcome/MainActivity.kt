package ru.pavellysov.carplaywelcome

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Switch
import android.widget.TextView

class MainActivity : android.app.Activity() {
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = preferences(this)
        val padding = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val enabled = Switch(this).apply {
            text = "Включить приветствия"
            isChecked = prefs.getBoolean(KEY_ENABLED, false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_ENABLED, checked).apply()
                if (checked) WelcomeService.start(this@MainActivity) else stopService(Intent(this@MainActivity, WelcomeService::class.java))
            }
        }
        val reconnect = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, (1..5).map { "$it мин." })
            setSelection(prefs.getInt(KEY_RECONNECT_MINUTES, 2) - 1)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    prefs.edit().putInt(KEY_RECONNECT_MINUTES, position + 1).apply()
                }
            }
        }
        val usageAccess = Button(this).apply {
            text = "Разрешить доступ к активности ZLink"
            setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }
        val phoneAccess = Button(this).apply {
            text = "Разрешить ожидание звонка"
            setOnClickListener { requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.POST_NOTIFICATIONS), 1) }
        }
        val test = Button(this).apply {
            text = "Проверить фразу для текущего времени"
            setOnClickListener {
                startForegroundService(Intent(this@MainActivity, WelcomeService::class.java).setAction(WelcomeService.ACTION_TEST))
            }
        }
        logView = TextView(this).apply { gravity = Gravity.START }
        layout.addView(enabled)
        layout.addView(TextView(this).apply { text = "Считать восстановлением после:" })
        layout.addView(reconnect)
        layout.addView(usageAccess)
        layout.addView(phoneAccess)
        layout.addView(test)
        layout.addView(TextView(this).apply { text = "Последнее событие:" })
        layout.addView(logView)
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        logView.text = preferences(this).getString(KEY_LOG, "Пока нет событий")
    }
}

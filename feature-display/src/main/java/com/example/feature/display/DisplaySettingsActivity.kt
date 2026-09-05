package com.example.feature.display

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.window.layout.WindowMetricsCalculator
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class DisplaySettingsActivity : AppCompatActivity() {

    private val tag = "LifecycleLog"
    private val activityName = "DisplaySettingsActivity"

    private lateinit var tvBrightnessValue: TextView
    private lateinit var sliderBrightness: Slider
    private lateinit var tvThemeStatus: TextView
    private lateinit var switchCleanScreen: MaterialSwitch

    private val providerUri by lazy {
        Uri.parse("content://com.example.carsettings.provider.display/items")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "[$activityName] onCreate(savedInstanceState = $savedInstanceState)")
        setContentView(R.layout.activity_display_settings)

        tvBrightnessValue = findViewById(R.id.tvBrightnessValue)
        sliderBrightness = findViewById(R.id.sliderBrightness)
        tvThemeStatus = findViewById(R.id.tvThemeStatus)
        switchCleanScreen = findViewById(R.id.switchCleanScreen)

        val btnThemeAuto = findViewById<Button>(R.id.btnThemeAuto)
        val btnThemeLight = findViewById<Button>(R.id.btnThemeLight)
        val btnThemeDark = findViewById<Button>(R.id.btnThemeDark)

        // Initial state sync
        sliderBrightness.value = DisplaySettingsProvider.brightness.toFloat()
        tvBrightnessValue.text = "${DisplaySettingsProvider.brightness}%"
        tvThemeStatus.text = "Current: ${DisplaySettingsProvider.themeMode}"
        switchCleanScreen.isChecked = DisplaySettingsProvider.cleanScreenMode

        // Slider listeners
        sliderBrightness.addOnChangeListener { _, value, fromUser ->
            val b = value.toInt()
            tvBrightnessValue.text = "$b%"
            if (fromUser) {
                updateSetting(DisplaySettingsProvider.KEY_BRIGHTNESS, b.toString())
            }
        }

        // Theme buttons
        btnThemeAuto.setOnClickListener { updateSetting(DisplaySettingsProvider.KEY_THEME_MODE, "AUTO") }
        btnThemeLight.setOnClickListener { updateSetting(DisplaySettingsProvider.KEY_THEME_MODE, "LIGHT") }
        btnThemeDark.setOnClickListener { updateSetting(DisplaySettingsProvider.KEY_THEME_MODE, "DARK") }

        // Switch listeners
        switchCleanScreen.setOnCheckedChangeListener { _, isChecked ->
            updateSetting(DisplaySettingsProvider.KEY_CLEAN_SCREEN_MODE, isChecked.toString())
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(tag, "[$activityName] onStart()")
    }

    override fun onResume() {
        super.onResume()
        val metrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this)
        val isEmbedded = try {
            androidx.window.embedding.ActivityEmbeddingController.getInstance(this).isActivityEmbedded(this)
        } catch (e: Exception) {
            false
        }
        Log.d(tag, "[$activityName] onResume() (isActivityEmbedded = $isEmbedded, bounds = ${metrics.bounds})")
    }

    override fun onPause() {
        super.onPause()
        Log.d(tag, "[$activityName] onPause()")
    }

    override fun onStop() {
        super.onStop()
        Log.d(tag, "[$activityName] onStop()")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "[$activityName] onDestroy()")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(tag, "[$activityName] onConfigurationChanged(newConfig = $newConfig)")
    }

    private fun updateSetting(key: String, value: String) {
        val extras = Bundle().apply {
            putString("extra_key", key)
            putString("extra_value", value)
        }
        try {
            contentResolver.call(providerUri, "update_item", null, extras)
            if (key == DisplaySettingsProvider.KEY_THEME_MODE) {
                tvThemeStatus.text = "Current: $value"
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to update display setting $key: $e")
        }
    }
}

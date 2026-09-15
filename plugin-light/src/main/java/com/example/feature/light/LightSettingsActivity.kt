package com.example.feature.light

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.window.layout.WindowMetricsCalculator
import com.google.android.material.materialswitch.MaterialSwitch

class LightSettingsActivity : AppCompatActivity() {

    private val tag = "LifecycleLog"
    private val activityName = "LightSettingsActivity"

    private lateinit var tvHeadlightsStatus: TextView
    private lateinit var switchAmbientLight: MaterialSwitch
    private lateinit var switchFrunkLight: MaterialSwitch
    private lateinit var switchTrunkLight: MaterialSwitch
    private lateinit var switchAutoHighBeam: MaterialSwitch

    private val providerUri by lazy {
        Uri.parse("content://com.example.carsettings.provider.light/items")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "[$activityName] onCreate(savedInstanceState = $savedInstanceState)")
        setContentView(R.layout.activity_light_settings)

        tvHeadlightsStatus = findViewById(R.id.tvHeadlightsStatus)
        switchAmbientLight = findViewById(R.id.switchAmbientLight)
        switchFrunkLight = findViewById(R.id.switchFrunkLight)
        switchTrunkLight = findViewById(R.id.switchTrunkLight)
        switchAutoHighBeam = findViewById(R.id.switchAutoHighBeam)

        val btnOff = findViewById<Button>(R.id.btnHeadlightOff)
        val btnPark = findViewById<Button>(R.id.btnHeadlightParking)
        val btnOn = findViewById<Button>(R.id.btnHeadlightOn)
        val btnAuto = findViewById<Button>(R.id.btnHeadlightAuto)

        // Initial state sync
        switchAmbientLight.isChecked = LightSettingsProvider.ambientLightEnabled
        switchFrunkLight.isChecked = LightSettingsProvider.frunkLightEnabled
        switchTrunkLight.isChecked = LightSettingsProvider.trunkLightEnabled
        switchAutoHighBeam.isChecked = LightSettingsProvider.autoHighBeamEnabled
        updateHeadlightUi(LightSettingsProvider.headlightsMode)

        // Listeners for Headlights Mode
        btnOff.setOnClickListener { updateSetting(LightSettingsProvider.KEY_HEADLIGHTS, "OFF") }
        btnPark.setOnClickListener { updateSetting(LightSettingsProvider.KEY_HEADLIGHTS, "PARKING") }
        btnOn.setOnClickListener { updateSetting(LightSettingsProvider.KEY_HEADLIGHTS, "ON") }
        btnAuto.setOnClickListener { updateSetting(LightSettingsProvider.KEY_HEADLIGHTS, "AUTO") }

        // Switch listeners
        switchAmbientLight.setOnCheckedChangeListener { _, isChecked ->
            updateSetting(LightSettingsProvider.KEY_AMBIENT_LIGHT, isChecked.toString())
        }
        switchFrunkLight.setOnCheckedChangeListener { _, isChecked ->
            updateSetting(LightSettingsProvider.KEY_FRUNK_LIGHT, isChecked.toString())
        }
        switchTrunkLight.setOnCheckedChangeListener { _, isChecked ->
            updateSetting(LightSettingsProvider.KEY_TRUNK_LIGHT, isChecked.toString())
        }
        switchAutoHighBeam.setOnCheckedChangeListener { _, isChecked ->
            updateSetting(LightSettingsProvider.KEY_AUTO_HIGH_BEAM, isChecked.toString())
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
            if (key == LightSettingsProvider.KEY_HEADLIGHTS) {
                updateHeadlightUi(value)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to update light setting $key: $e")
        }
    }

    private fun updateHeadlightUi(mode: String) {
        tvHeadlightsStatus.text = "Current: $mode ${if (mode == "AUTO") "(Sensor Active)" else ""}"
    }
}

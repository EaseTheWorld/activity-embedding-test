package com.example.feature.sound

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.window.layout.WindowMetricsCalculator
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class SoundSettingsActivity : AppCompatActivity() {

    private val tag = "LifecycleLog"
    private val activityName = "SoundSettingsActivity"

    private lateinit var tvMediaVolumeValue: TextView
    private lateinit var tvNavVolumeValue: TextView
    private lateinit var sliderMediaVolume: Slider
    private lateinit var sliderNavVolume: Slider
    private lateinit var switchSurroundSound: MaterialSwitch
    private lateinit var switchTouchFeedback: MaterialSwitch

    private val providerUri by lazy {
        Uri.parse("content://com.example.carsettings.provider.sound/items")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "[$activityName] onCreate(savedInstanceState = $savedInstanceState)")
        setContentView(R.layout.activity_sound_settings)

        tvMediaVolumeValue = findViewById(R.id.tvMediaVolumeValue)
        tvNavVolumeValue = findViewById(R.id.tvNavVolumeValue)
        sliderMediaVolume = findViewById(R.id.sliderMediaVolume)
        sliderNavVolume = findViewById(R.id.sliderNavVolume)
        switchSurroundSound = findViewById(R.id.switchSurroundSound)
        switchTouchFeedback = findViewById(R.id.switchTouchFeedback)

        // Initial state sync
        sliderMediaVolume.value = SoundSettingsProvider.mediaVolume.toFloat()
        sliderNavVolume.value = SoundSettingsProvider.navVolume.toFloat()
        tvMediaVolumeValue.text = "${SoundSettingsProvider.mediaVolume}%"
        tvNavVolumeValue.text = "${SoundSettingsProvider.navVolume}%"
        switchSurroundSound.isChecked = SoundSettingsProvider.surroundSoundEnabled
        switchTouchFeedback.isChecked = SoundSettingsProvider.touchFeedbackEnabled

        // Slider listeners
        sliderMediaVolume.addOnChangeListener { _, value, fromUser ->
            val vol = value.toInt()
            tvMediaVolumeValue.text = "$vol%"
            if (fromUser) {
                updateSetting(SoundSettingsProvider.KEY_MEDIA_VOLUME, vol.toString())
            }
        }

        sliderNavVolume.addOnChangeListener { _, value, fromUser ->
            val vol = value.toInt()
            tvNavVolumeValue.text = "$vol%"
            if (fromUser) {
                updateSetting(SoundSettingsProvider.KEY_NAV_VOLUME, vol.toString())
            }
        }

        // Switch listeners
        switchSurroundSound.setOnCheckedChangeListener { _, isChecked ->
            updateSetting(SoundSettingsProvider.KEY_SURROUND_SOUND, isChecked.toString())
        }
        switchTouchFeedback.setOnCheckedChangeListener { _, isChecked ->
            updateSetting(SoundSettingsProvider.KEY_TOUCH_FEEDBACK, isChecked.toString())
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
        } catch (e: Exception) {
            Log.e(tag, "Failed to update sound setting $key: $e")
        }
    }
}

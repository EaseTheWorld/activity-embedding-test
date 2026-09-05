package com.example.lifecycleapp

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.window.layout.WindowMetricsCalculator

abstract class BaseLoggingActivity : AppCompatActivity() {

    protected val tag: String = "LifecycleLog"
    protected val activityName: String
        get() = this::class.java.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "[$activityName] onCreate(savedInstanceState = $savedInstanceState)")
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d(tag, "[$activityName] onSaveInstanceState(outState = $outState)")
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        Log.d(tag, "[$activityName] onRestoreInstanceState(savedInstanceState = $savedInstanceState)")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(tag, "[$activityName] onConfigurationChanged(newConfig = $newConfig)")
    }
}

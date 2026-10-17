package com.example.lifecycleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.sample.SampleSettingsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SampleSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleSettingsScreen()
        }
    }
}

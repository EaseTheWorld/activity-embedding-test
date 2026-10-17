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
        val initialDeepLinkKey = intent?.getStringExtra("target_key")
            ?: intent?.data?.getQueryParameter("target_key")
            ?: intent?.data?.path?.removePrefix("/")
        val initialSearchQuery = intent?.getStringExtra("search_query")
            ?: intent?.data?.getQueryParameter("search_query")
        setContent {
            SampleSettingsScreen(
                initialDeepLinkKey = initialDeepLinkKey,
                initialSearchQuery = initialSearchQuery
            )
        }
    }
}

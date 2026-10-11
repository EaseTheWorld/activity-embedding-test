package com.example.common.ui.settings

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class GenericSubActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PARENT_TITLE = "extra_parent_title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parentTitle = intent.getStringExtra(EXTRA_PARENT_TITLE) ?: "Settings"
        Log.d("LifecycleLog", "[GenericSubActivity] onCreate(parentTitle=$parentTitle)")

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFFEEF2FF)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$parentTitle - Sub Activity",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1E2E)
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Started via normal startActivity() from GenericSettingsActivity",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color(0xFF4B5563)
                        )
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { finish() }) {
                        Text("Back to $parentTitle")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LifecycleLog", "[GenericSubActivity] onDestroy()")
    }
}

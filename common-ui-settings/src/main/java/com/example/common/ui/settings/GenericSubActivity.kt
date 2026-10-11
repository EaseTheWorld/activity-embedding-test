package com.example.common.ui.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Sub-Activity launched inside the active CategoryStack (without ACTION_SETTINGS_EMBED)
 * to verify intra-category Activity stacking, Back navigation, and atomic stack replacement
 * via `CategoryStackManager.finishCategoryStacks()`.
 */
class GenericSubActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PARENT_TITLE = "extra_parent_title"
        const val EXTRA_SUB_DEPTH = "extra_sub_depth"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parentTitle = intent.getStringExtra(EXTRA_PARENT_TITLE) ?: "Settings"
        val depth = intent.getIntExtra(EXTRA_SUB_DEPTH, 1)
        Log.d("LifecycleLog", "[GenericSubActivity] onCreate (parent=$parentTitle, depth=$depth)")

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFFF6F7FB)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF1E1E2E)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "$parentTitle — SubActivity #$depth",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E1E2E)
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Stacked inside Current CategoryStack (TaskFragment)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF6B7280)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Category ActivityStack Depth: $depth",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E1E2E)
                                )
                            )
                            Text(
                                text = "• Pressing Back returns to the previous Activity in this CategoryStack.\n" +
                                    "• Selecting another Category or Home in the menu calls finishActivityStacks() to clear this entire CategoryStack at once.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF4B5563)
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        startActivity(
                                            Intent(this@GenericSubActivity, GenericSubActivity::class.java).apply {
                                                putExtra(EXTRA_PARENT_TITLE, parentTitle)
                                                putExtra(EXTRA_SUB_DEPTH, depth + 1)
                                            }
                                        )
                                    }
                                ) {
                                    Text("Push SubActivity #${depth + 1}")
                                }
                                OutlinedButton(onClick = { finish() }) {
                                    Text("Back (Finish Current)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val depth = intent.getIntExtra(EXTRA_SUB_DEPTH, 1)
        Log.d("LifecycleLog", "[GenericSubActivity] onDestroy (depth=$depth)")
    }
}

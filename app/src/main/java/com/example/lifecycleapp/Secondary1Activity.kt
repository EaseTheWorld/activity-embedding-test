package com.example.lifecycleapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button

class Secondary1Activity : BaseLoggingActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secondary1)

        val btnSetResultOnly = findViewById<Button>(R.id.btnSetResultOnly)
        val btnSaveResult = findViewById<Button>(R.id.btnSaveResult)
        val btnGoToSecondary2 = findViewById<Button>(R.id.btnGoToSecondary2)
        val btnBack = findViewById<Button>(R.id.btnBack)

        btnSetResultOnly.setOnClickListener {
            val resultIntent = Intent().apply {
                putExtra("EXTRA_RESULT", "Pre-set DATA from Secondary1 (before finishActivity)")
            }
            android.util.Log.d(tag, "[$activityName] Called setResult(RESULT_OK, data) WITHOUT calling finish()")
            setResult(RESULT_OK, resultIntent)
        }

        btnSaveResult.setOnClickListener {
            val resultIntent = Intent().apply {
                putExtra("EXTRA_RESULT", "Success: Item 1 Saved at ${System.currentTimeMillis()}")
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        btnGoToSecondary2.setOnClickListener {
            val intent = Intent(this, Secondary2Activity::class.java)
            startActivity(intent)
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
}

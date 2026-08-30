package com.example.lifecycleapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button

class Secondary1Activity : BaseLoggingActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secondary1)

        val btnGoToSecondary2 = findViewById<Button>(R.id.btnGoToSecondary2)
        val btnBack = findViewById<Button>(R.id.btnBack)

        btnGoToSecondary2.setOnClickListener {
            val intent = Intent(this, Secondary2Activity::class.java)
            startActivity(intent)
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
}

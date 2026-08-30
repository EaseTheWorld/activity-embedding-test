package com.example.lifecycleapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button

class Secondary2Activity : BaseLoggingActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secondary2)

        val btnGoToSecondary1 = findViewById<Button>(R.id.btnGoToSecondary1)
        val btnBack = findViewById<Button>(R.id.btnBack)

        btnGoToSecondary1.setOnClickListener {
            val intent = Intent(this, Secondary1Activity::class.java)
            startActivity(intent)
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
}

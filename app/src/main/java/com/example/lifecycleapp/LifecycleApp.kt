package com.example.lifecycleapp

import android.app.Application
import android.util.Log

class LifecycleApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("LifecycleLog", "[LifecycleApp] onCreate()")
    }
}

package com.example.khetmitra

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_splash)
        Handler(Looper.getMainLooper()).postDelayed({

            val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
            val isSetupComplete = prefs.getBoolean("IsLanguageSet", false)

            val nextActivity = if (isSetupComplete) {
                Intent(this, LoginActivity::class.java)
            } else {
                Intent(this, LanguageSelectionActivity::class.java)
            }

            startActivity(nextActivity)
            finish()

        }, 3000)
    }
}
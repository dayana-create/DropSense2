package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        handler.postDelayed({
            val preferences = getSharedPreferences("app_preferences", MODE_PRIVATE)
            val onboardingCompleted = preferences.getBoolean("onboarding_completed", false)
            val isLoggedIn = preferences.getBoolean("is_logged_in", false)

            val nextActivity = when {
                !onboardingCompleted -> OnboardingActivity::class.java
                !isLoggedIn -> LoginActivity::class.java
                else -> MainActivity::class.java
            }

            startActivity(Intent(this, nextActivity))
            finish()
        }, 1200)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
package com.cherry.wakeupschedule

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Handler(Looper.getMainLooper()).postDelayed({
            val targetActivity = if (IntroActivity.isFirstLaunch(this)) {
                IntroActivity::class.java
            } else {
                MainActivity::class.java
            }
            val intent = Intent(this, targetActivity)
            startActivity(intent)
            finish()
        }, 1000)
    }
}
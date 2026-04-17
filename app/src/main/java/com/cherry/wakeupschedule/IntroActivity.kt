package com.cherry.wakeupschedule

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cherry.wakeupschedule.databinding.ActivityIntroBinding

class IntroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIntroBinding
    private var currentPage = 0

    private lateinit var indicators: List<View>

    companion object {
        private const val PREF_NAME = "app_prefs"
        private const val KEY_FIRST_LAUNCH = "first_launch"

        fun isFirstLaunch(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        }

        fun setFirstLaunchComplete(context: Context) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initIndicators()
        setupViews()
    }

    private fun initIndicators() {
        indicators = listOf(
            findViewById(R.id.indicator_1),
            findViewById(R.id.indicator_2),
            findViewById(R.id.indicator_3),
            findViewById(R.id.indicator_4),
            findViewById(R.id.indicator_5),
            findViewById(R.id.indicator_6)
        )
    }

    private fun setupViews() {
        binding.btnNext.setOnClickListener {
            navigateNext()
        }
    }

    private fun navigateNext() {
        if (currentPage < 5) {
            currentPage++
            binding.viewFlipper.displayedChild = currentPage
            updateIndicators()
            updateButtonText()
        } else {
            finishIntro()
        }
    }

    private fun updateIndicators() {
        indicators.forEachIndexed { index, view ->
            if (view != null) {
                view.setBackgroundColor(
                    if (index == currentPage) 0xFFFFFFFF.toInt() else 0x66FFFFFF
                )
            }
        }
    }

    private fun updateButtonText() {
        binding.btnNext.text = if (currentPage == 5) "开始使用" else "下一步"
    }

    private fun finishIntro() {
        setFirstLaunchComplete(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

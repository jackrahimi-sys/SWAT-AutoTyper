package com.swat.autotyper

import android.inputmethodservice.InputMethodService
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class SwatInputMethodService : InputMethodService() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var currentIndex = 0
    private var fullText = ""
    private var delayMs = 100L
    private var countdownSeconds = 5L
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreateInputView(): View {
        val prefs = getSharedPreferences("swat_prefs", Context.MODE_PRIVATE)
        fullText = prefs.getString("text", "") ?: ""
        delayMs = prefs.getLong("delay_ms", 100L).coerceIn(20, 5000)
        countdownSeconds = prefs.getLong("countdown_s", 5L).coerceIn(0, 120)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 10)
            setBackgroundColor(Color.rgb(18, 22, 32))
        }
        status = TextView(this).apply {
            text = if (fullText.isBlank()) "اول متن را در SWAT AutoTyper ذخیره کن" else "SWAT آماده است • ${fullText.length} نویسه"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(4, 4, 4, 8)
        }
        panel.addView(status, LinearLayout.LayoutParams(-1, -2))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        startButton = Button(this).apply {
            text = "شروع تایپ"
            isAllCaps = false
            setOnClickListener { startTyping() }
        }
        stopButton = Button(this).apply {
            text = "توقف"
            isAllCaps = false
            setOnClickListener { stopTyping("متوقف شد") }
            isEnabled = false
        }
        buttons.addView(startButton, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(stopButton, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(buttons)
        panel.addView(TextView(this).apply {
            text = "متن را در برنامه اصلی تغییر بده؛ این کیبورد متن را حرف‌به‌حرف وارد می‌کند."
            setTextColor(Color.LTGRAY)
            textSize = 11f
            gravity = Gravity.CENTER
        })
        return panel
    }

    private fun startTyping() {
        if (running) return
        val prefs = getSharedPreferences("swat_prefs", Context.MODE_PRIVATE)
        fullText = prefs.getString("text", "") ?: ""
        delayMs = prefs.getLong("delay_ms", 100L).coerceIn(20, 5000)
        countdownSeconds = prefs.getLong("countdown_s", 5L).coerceIn(0, 120)
        if (fullText.isBlank()) {
            status.text = "متنی ذخیره نشده؛ برنامه اصلی را باز کن"
            return
        }
        if (currentInputConnection == null) {
            status.text = "اول روی کادر پیام بزن"
            return
        }
        running = true
        currentIndex = 0
        startButton.isEnabled = false
        stopButton.isEnabled = true
        countdownTick(countdownSeconds)
    }

    private fun countdownTick(secondsLeft: Long) {
        if (!running) return
        if (secondsLeft <= 0) {
            status.text = "در حال تایپ…"
            typeNext()
        } else {
            status.text = "شروع در $secondsLeft ثانیه…"
            handler.postDelayed({ countdownTick(secondsLeft - 1) }, 1000)
        }
    }

    private fun typeNext() {
        if (!running) return
        if (currentIndex >= fullText.length) {
            stopTyping("تایپ تمام شد")
            return
        }
        val connection = currentInputConnection
        if (connection == null) {
            stopTyping("کادر متن دیگر فعال نیست")
            return
        }
        val ch = fullText[currentIndex].toString()
        connection.commitText(ch, 1)
        currentIndex++
        status.text = "در حال تایپ… $currentIndex / ${fullText.length}"
        handler.postDelayed({ typeNext() }, delayMs)
    }

    private fun stopTyping(message: String) {
        running = false
        handler.removeCallbacksAndMessages(null)
        if (::status.isInitialized) status.text = message
        if (::startButton.isInitialized) startButton.isEnabled = true
        if (::stopButton.isInitialized) stopButton.isEnabled = false
    }

    override fun onFinishInput() {
        stopTyping("کادر متن بسته شد")
        super.onFinishInput()
    }

    override fun onDestroy() {
        stopTyping("متوقف شد")
        super.onDestroy()
    }
}

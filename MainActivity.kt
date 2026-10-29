package com.swat.autotyper

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("swat_prefs", Context.MODE_PRIVATE) }
    private lateinit var editor: EditText
    private lateinit var delayInput: EditText
    private lateinit var countdownInput: EditText
    private lateinit var historyBox: LinearLayout
    private val screenRecordRequest = 714

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadSaved()
        refreshHistory()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
            setBackgroundColor(0xFF10131B.toInt())
        }
        scroll.addView(content)
        setContentView(scroll)

        content.addView(TextView(this).apply {
            text = "SWAT AutoTyper"
            textSize = 27f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        })
        content.addView(TextView(this).apply {
            text = "متن را آماده کن، کیبورد SWAT را در روبیکا انتخاب کن و تایپ را شروع کن."
            textSize = 14f
            setTextColor(0xFFCDD3E0.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(18))
        })

        editor = EditText(this).apply {
            hint = "متن موردنظر را اینجا بنویس..."
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9AA4B8.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFF252B39.toInt())
        }
        content.addView(editor, matchWrap())

        content.addView(label("فاصله بین هر حرف (میلی‌ثانیه)"))
        delayInput = numberInput("مثلاً 100")
        content.addView(delayInput, matchWrap())

        content.addView(label("شمارش معکوس قبل از شروع (ثانیه)"))
        countdownInput = numberInput("مثلاً 5")
        content.addView(countdownInput, matchWrap())

        content.addView(actionButton("ذخیره متن و تنظیمات") { saveCurrent() })
        content.addView(actionButton("انتخاب کیبورد SWAT") {
            saveCurrent()
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        content.addView(actionButton("باز کردن انتخاب‌گر کیبورد") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        content.addView(actionButton("راهنمای استفاده در روبیکا") { showRubikaHelp() })
        content.addView(actionButton("شروع ضبط صفحه") { requestScreenRecording() })
        content.addView(actionButton("توقف ضبط صفحه") {
            startService(Intent(this, ScreenRecordService::class.java).setAction(ScreenRecordService.ACTION_STOP))
            toast("درخواست توقف ضبط ارسال شد")
        })

        content.addView(label("متن‌های ذخیره‌شده و تاریخچه"))
        historyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(historyBox)
        content.addView(TextView(this).apply {
            text = "نکته: ضبط صفحه فقط پس از تأیید پنجره رسمی اندروید آغاز می‌شود. فایل‌های ضبط‌شده ممکن است شامل پیام‌ها و اطلاعات خصوصی روی صفحه باشند."
            textSize = 12f
            setTextColor(0xFFADB6C8.toInt())
            setPadding(0, dp(18), 0, 0)
        })
    }

    private fun label(textValue: String) = TextView(this).apply {
        text = textValue
        textSize = 14f
        setTextColor(0xFFE4E9F2.toInt())
        setPadding(0, dp(14), 0, dp(6))
    }

    private fun numberInput(hintText: String) = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_NUMBER
        setTextColor(0xFFFFFFFF.toInt())
        setHintTextColor(0xFF9AA4B8.toInt())
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setBackgroundColor(0xFF252B39.toInt())
    }

    private fun actionButton(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(8) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun saveCurrent() {
        val text = editor.text.toString()
        val delay = delayInput.text.toString().toLongOrNull()?.coerceIn(20, 5000) ?: 100L
        val countdown = countdownInput.text.toString().toLongOrNull()?.coerceIn(0, 120) ?: 5L
        prefs.edit()
            .putString("text", text)
            .putLong("delay_ms", delay)
            .putLong("countdown_s", countdown)
            .apply()
        if (text.isNotBlank()) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val old = prefs.getString("history", "") ?: ""
            val entry = "$timestamp\t$text"
            prefs.edit().putString("history", (entry + "\n" + old).take(12000)).apply()
        }
        refreshHistory()
        toast("متن و تنظیمات ذخیره شد")
    }

    private fun loadSaved() {
        editor.setText(prefs.getString("text", ""))
        delayInput.setText((prefs.getLong("delay_ms", 100L)).toString())
        countdownInput.setText((prefs.getLong("countdown_s", 5L)).toString())
    }

    private fun refreshHistory() {
        if (!::historyBox.isInitialized) return
        historyBox.removeAllViews()
        val entries = (prefs.getString("history", "") ?: "").lines().filter { it.isNotBlank() }.take(20)
        if (entries.isEmpty()) {
            historyBox.addView(label("هنوز موردی ذخیره نشده است"))
            return
        }
        entries.forEach { line ->
            val parts = line.split('\t', limit = 2)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val preview = TextView(this).apply {
                text = (parts.firstOrNull().orEmpty() + "\n" + parts.getOrElse(1) { "" }).take(100)
                setTextColor(0xFFE4E9F2.toInt())
                textSize = 12f
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            row.addView(preview, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply {
                text = "بارگذاری"
                isAllCaps = false
                setOnClickListener {
                    editor.setText(parts.getOrElse(1) { "" })
                    toast("متن بارگذاری شد")
                }
            })
            historyBox.addView(row)
            val divider = android.view.View(this).apply { setBackgroundColor(0xFF303748.toInt()) }
            historyBox.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        }
    }

    private fun showRubikaHelp() {
        AlertDialog.Builder(this)
            .setTitle("استفاده در روبیکا")
            .setMessage(
                "۱) متن را ذخیره کن.\n" +
                "۲) «انتخاب کیبورد SWAT» را بزن و SWAT AutoTyper را فعال کن.\n" +
                "۳) روبیکا را باز کن و وارد کادر پیام شو.\n" +
                "۴) از انتخاب‌گر کیبورد، SWAT AutoTyper را انتخاب کن.\n" +
                "۵) دکمه شروع را روی کیبورد بزن. توقف هم از همان‌جا ممکن است.\n\n" +
                "تایپ از طریق کیبوردی انجام می‌شود که خودت انتخاب کرده‌ای؛ برنامه پیام را خودکار ارسال نمی‌کند."
            )
            .setPositiveButton("متوجه شدم", null)
            .show()
    }

    private fun requestScreenRecording() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), screenRecordRequest)
    }

    @Deprecated("Activity result API kept simple for this starter project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == screenRecordRequest && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_START
                putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenRecordService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
            toast("ضبط صفحه آغاز شد")
        } else if (requestCode == screenRecordRequest) {
            toast("ضبط صفحه لغو شد")
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

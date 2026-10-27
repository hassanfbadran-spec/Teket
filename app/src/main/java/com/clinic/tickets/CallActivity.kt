package com.clinic.tickets

import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class CallActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var db: Db
    private lateinit var numberView: TextView
    private lateinit var typeView: TextView
    private lateinit var waitingView: TextView
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Db(this)
        tts = TextToSpeech(this, this)

        val root = column()
        root.addView(header("الرقم الحالي"), wrapParams())
        numberView = label("-", 110f, true, true).apply { setTextColor(C.DARK) }
        typeView = label("", 30f, true, true).apply { setTextColor(C.GREEN) }
        waitingView = label("", 20f, false, true)
        root.addView(numberView, inColumn(1f))
        root.addView(typeView, wrapParams())
        root.addView(waitingView, wrapParams())

        val r1 = row()
        r1.addView(button("التالي", 26f, C.GREEN) { next() }, inRow(1f, dp(80)))
        root.addView(r1, wrapParams())

        val r2 = row()
        r2.addView(button("إعادة نداء", 20f) { recall() }, inRow())
        r2.addView(button("تخطي", 20f) { skip() }, inRow())
        root.addView(r2, wrapParams())

        root.addView(button("رجوع", 18f, C.GRAY) { finish() }, wrapParams())
        setContentView(root)
        refresh()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts?.setLanguage(Locale("ar"))
            ttsReady = r != null && r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun refresh() {
        val t = db.current()
        numberView.text = t?.number?.toString() ?: "-"
        typeView.text = t?.type ?: ""
        waitingView.text = "المنتظرون: ${db.waitingTotal()}"
    }

    private fun next() = handle(db.callNext())

    private fun skip() = handle(db.skipCurrent())

    private fun handle(t: Ticket?) {
        if (t == null) {
            Toast.makeText(this, "لا يوجد منتظرون", Toast.LENGTH_SHORT).show()
        } else {
            speak(t)
        }
        refresh()
    }

    private fun recall() {
        val t = db.current()
        if (t == null) Toast.makeText(this, "لا يوجد رقم حالي", Toast.LENGTH_SHORT).show() else speak(t)
    }

    // النداء الصوتي يحتاج صوت عربي مثبت في الجهاز، وإن لم يوجد يتجاهله البرنامج بصمت
    private fun speak(t: Ticket) {
        if (ttsReady) tts?.speak("على رقم ${t.number}، ${t.type}", TextToSpeech.QUEUE_FLUSH, null, "call")
    }
}

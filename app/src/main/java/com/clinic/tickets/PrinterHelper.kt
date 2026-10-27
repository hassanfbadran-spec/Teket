package com.clinic.tickets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Block(val text: String, val size: Float, val bold: Boolean = false, val center: Boolean = true)

/**
 * الطباعة تتم برسم التذكرة كصورة (Bitmap) ثم إرسالها لطابعة Sunmi،
 * عشان العربي يطلع سليم ومتصل بغض النظر عن خطوط الطابعة.
 */
class PrinterHelper(private val ctx: Context) {

    private var service: SunmiPrinterService? = null

    private val callback = object : InnerPrinterCallback() {
        override fun onConnected(service: SunmiPrinterService) {
            this@PrinterHelper.service = service
        }

        override fun onDisconnected() {
            this@PrinterHelper.service = null
        }
    }

    fun connect() {
        try {
            InnerPrinterManager.getInstance().bindService(ctx, callback)
        } catch (e: Exception) {
        }
    }

    fun disconnect() {
        try {
            InnerPrinterManager.getInstance().unBindService(ctx, callback)
        } catch (e: Exception) {
        }
        service = null
    }

    fun isReady() = service != null

    fun printTicket(clinic: String, doctor: String, footer: String, t: Ticket, ahead: Int): Boolean {
        val whenText = SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US).format(Date(t.issuedAt))
        val blocks = mutableListOf<Block>()
        blocks.add(Block(clinic, 30f, true))
        if (doctor.isNotBlank()) blocks.add(Block(doctor, 22f))
        blocks.add(Block("──────────────", 22f))
        blocks.add(Block(t.type, 34f, true))
        blocks.add(Block(t.number.toString(), 120f, true))
        blocks.add(Block("عدد المنتظرين قبلك: $ahead", 24f))
        blocks.add(Block(whenText, 20f))
        if (footer.isNotBlank()) blocks.add(Block(footer, 20f))
        return print(blocks)
    }

    fun printSummary(clinic: String, title: String, lines: List<String>): Boolean {
        val blocks = mutableListOf<Block>()
        blocks.add(Block(clinic, 28f, true))
        blocks.add(Block(title, 24f, true))
        blocks.add(Block("──────────────", 22f))
        lines.forEach { blocks.add(Block(it, 22f, false, false)) }
        blocks.add(Block(SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US).format(Date()), 18f))
        return print(blocks)
    }

    private fun print(blocks: List<Block>): Boolean {
        val s = service ?: return false
        return try {
            val bmp = render(blocks)
            s.printerInit(null)
            s.setAlignment(1, null)
            s.printBitmap(bmp, null)
            s.lineWrap(4, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun render(blocks: List<Block>): Bitmap {
        val width = 384 // عرض ورق 58mm بالنقاط
        val gap = 8
        val layouts = blocks.map { b ->
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = b.size
                typeface = if (b.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            StaticLayout.Builder.obtain(b.text, 0, b.text.length, paint, width - 16)
                .setAlignment(if (b.center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .build()
        }
        val height = layouts.sumOf { it.height + gap } + 16
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        var y = 8f
        for (l in layouts) {
            canvas.save()
            canvas.translate(8f, y)
            l.draw(canvas)
            canvas.restore()
            y += l.height + gap
        }
        return bmp
    }
}

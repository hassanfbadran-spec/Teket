package com.clinic.tickets

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class Block(val text: String, val size: Float, val center: Boolean = true)

/**
 * الطباعة: تُرسم التذكرة كصورة، ثم تتحول لأبيض وأسود صريح (بدون رمادي) فتطلع داكنة وواضحة.
 * - الطابعة المدمجة Sunmi: عبر Sunmi Printer SDK.
 * - أي طابعة بلوتوث: عبر أوامر ESC/POS.
 */
class PrinterHelper(private val ctx: Context) {

    private var service: SunmiPrinterService? = null
    private val main = Handler(Looper.getMainLooper())

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

    private fun usingBluetooth() = Settings.btAddress(ctx).isNotEmpty()

    @SuppressLint("MissingPermission")
    fun isReady(): Boolean {
        if (usingBluetooth()) {
            return try {
                BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
            } catch (e: Exception) {
                false
            }
        }
        return service != null
    }

    fun statusText(): String = when {
        usingBluetooth() -> if (isReady()) "بلوتوث: ${Settings.btName(ctx)}" else "البلوتوث مغلق"
        service != null -> "Sunmi المدمجة"
        else -> "غير متصلة"
    }

    fun printTicket(
        clinic: String, doctor: String, footer: String, extras: List<String>,
        t: Ticket, ahead: Int, onDone: (Boolean) -> Unit
    ) {
        val whenText = SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US).format(Date(t.issuedAt))
        val blocks = mutableListOf<Block>()
        blocks.add(Block(clinic, 34f))
        if (doctor.isNotBlank()) blocks.add(Block(doctor, 26f))
        blocks.add(Block("──────────────", 26f))
        blocks.add(Block(t.type, 38f))
        blocks.add(Block(t.number.toString(), 130f))
        blocks.add(Block("عدد المنتظرين قبلك: $ahead", 28f))
        blocks.add(Block(whenText, 24f))
        if (footer.isNotBlank()) blocks.add(Block(footer, 24f))
        extras.filter { it.isNotBlank() }.forEach { blocks.add(Block(it, 24f)) }
        print(blocks, onDone)
    }

    fun printSummary(clinic: String, title: String, lines: List<String>, onDone: (Boolean) -> Unit) {
        val blocks = mutableListOf<Block>()
        blocks.add(Block(clinic, 32f))
        blocks.add(Block(title, 28f))
        blocks.add(Block("──────────────", 26f))
        lines.forEach { blocks.add(Block(it, 24f, false)) }
        blocks.add(Block(SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US).format(Date()), 22f))
        print(blocks, onDone)
    }

    private fun print(blocks: List<Block>, onDone: (Boolean) -> Unit) {
        val bt = usingBluetooth()
        val sunmi = service
        if (!bt && sunmi == null) {
            onDone(false)
            return
        }
        Thread {
            val ok = try {
                val dots = if (bt) Settings.paperDots(ctx) else 384
                val bmp = toBw(render(blocks, dots))
                if (bt) {
                    sendBluetooth(bmp)
                } else {
                    sunmi!!.printerInit(null)
                    sunmi.setAlignment(1, null)
                    sunmi.printBitmap(bmp, null)
                    sunmi.lineWrap(4, null)
                    true
                }
            } catch (e: Exception) {
                false
            }
            main.post { onDone(ok) }
        }.start()
    }

    private fun render(blocks: List<Block>, width: Int): Bitmap {
        val gap = 8
        val layouts = blocks.map { b ->
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = b.size
                typeface = Typeface.DEFAULT_BOLD
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

    /** تحويل لأبيض وأسود صريح: أي بكسل أغمق من العتبة يصبح أسود تماماً */
    private fun toBw(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            px[i] = if (lum < 190) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    @SuppressLint("MissingPermission")
    private fun sendBluetooth(bmp: Bitmap): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        val device = adapter.getRemoteDevice(Settings.btAddress(ctx))
        adapter.cancelDiscovery()
        val socket = device.createRfcommSocketToServiceRecord(
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        )
        try {
            socket.connect()
            val out = socket.outputStream
            out.write(escposRaster(bmp))
            out.flush()
            Thread.sleep(1000)
            return true
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
            }
        }
    }

    /** أوامر ESC/POS: تهيئة، ثم الصورة على شرائح (GS v 0)، ثم تغذية ورق */
    private fun escposRaster(bmp: Bitmap): ByteArray {
        val w = bmp.width
        val h = bmp.height
        val wb = (w + 7) / 8
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x1B, 0x40))
        var y = 0
        while (y < h) {
            val band = minOf(128, h - y)
            out.write(
                byteArrayOf(
                    0x1D, 0x76, 0x30, 0x00,
                    (wb and 0xFF).toByte(), (wb shr 8).toByte(),
                    (band and 0xFF).toByte(), (band shr 8).toByte()
                )
            )
            for (r in 0 until band) {
                for (bx in 0 until wb) {
                    var b = 0
                    for (bit in 0 until 8) {
                        val x = bx * 8 + bit
                        if (x < w && (px[(y + r) * w + x] and 0xFF) == 0) b = b or (0x80 shr bit)
                    }
                    out.write(b)
                }
            }
            y += band
        }
        out.write(byteArrayOf(0x1B, 0x64, 0x04))
        return out.toByteArray()
    }
}

package com.clinic.tickets

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
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
import java.io.IOException
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

    /** سبب آخر فشل في الطباعة، لعرضه للمستخدم */
    var lastError: String = ""
        private set

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
        if (ahead >= 0) blocks.add(Block("عدد المنتظرين قبلك: $ahead", 28f))
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
            lastError = "الطابعة المدمجة غير متصلة"
            onDone(false)
            return
        }
        Thread {
            val ok = try {
                val dots = if (bt) Settings.paperDots(ctx) else 384
                val bmp = toBw(render(blocks, dots))
                if (bt) {
                    sendBluetooth(bmp)
                    true
                } else {
                    sunmi!!.printerInit(null)
                    sunmi.setAlignment(1, null)
                    sunmi.printBitmap(bmp, null)
                    sunmi.lineWrap(4, null)
                    true
                }
            } catch (e: Exception) {
                lastError = describe(e)
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

    private val spp: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /**
     * يجرّب 3 طرق اتصال بالترتيب (عادي، غير مؤمّن، قناة مباشرة)، ويعيد المحاولة مرتين،
     * ويرسل البيانات على دفعات صغيرة لأن بعض الطابعات تفشل مع الإرسال الكبير.
     */
    @SuppressLint("MissingPermission")
    private fun sendBluetooth(bmp: Bitmap) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw IOException("الجهاز لا يدعم البلوتوث")
        if (!adapter.isEnabled) throw IOException("البلوتوث مغلق، فعّله وحاول مرة أخرى")
        val device = adapter.getRemoteDevice(Settings.btAddress(ctx))
        val data = escposRaster(bmp)
        var last: Exception? = null
        for (attempt in 0 until 2) {
            for (strategy in 0..2) {
                var socket: BluetoothSocket? = null
                try {
                    val sock: BluetoothSocket = when (strategy) {
                        0 -> device.createRfcommSocketToServiceRecord(spp)
                        1 -> device.createInsecureRfcommSocketToServiceRecord(spp)
                        else -> device.javaClass
                            .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                            .invoke(device, 1) as BluetoothSocket
                    }
                    socket = sock
                    sock.connect()
                    val out = sock.outputStream
                    var i = 0
                    while (i < data.size) {
                        val n = minOf(512, data.size - i)
                        out.write(data, i, n)
                        out.flush()
                        i += n
                        Thread.sleep(30)
                    }
                    Thread.sleep(1200)
                    return
                } catch (e: SecurityException) {
                    throw e
                } catch (e: Exception) {
                    last = e
                } finally {
                    try {
                        socket?.close()
                    } catch (e: Exception) {
                    }
                }
            }
            Thread.sleep(500)
        }
        throw IOException("فشل الاتصال بالطابعة بكل الطرق", last)
    }

    private fun describe(e: Exception): String = when {
        e is SecurityException -> "إذن البلوتوث مرفوض من النظام (${e.message ?: ""})"
        e is IllegalArgumentException -> "عنوان الطابعة غير صحيح، اخترها من جديد من الإعدادات"
        e is IOException && e.cause != null ->
            "فشل الاتصال، تأكد أن الطابعة شغالة ومقترنة وغير متصلة بجهاز آخر (${e.cause?.message ?: ""})"
        e is IOException -> e.message ?: "خطأ في الاتصال"
        else -> e.message ?: e.javaClass.simpleName
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

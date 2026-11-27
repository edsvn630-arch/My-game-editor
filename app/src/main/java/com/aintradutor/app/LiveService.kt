package com.aintradutor.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast

class LiveService : Service() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var button: TextView? = null
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var vdisplay: VirtualDisplay? = null
    private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        if (projection != null) return START_NOT_STICKY
        val code = intent?.getIntExtra("code", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = mpm.getMediaProjection(code, data)
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    handler.post { stopSelf() }
                }
            }, handler)
            projection = p
            setupCapture(p)
            showButton()
        } catch (e: Throwable) {
            toast("Erro ao ligar o modo ao vivo: ${e.message}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun toast(m: String) {
        handler.post { Toast.makeText(applicationContext, m, Toast.LENGTH_LONG).show() }
    }

    @Suppress("DEPRECATION")
    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, LiveService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b: Notification.Builder
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("live", "Modo ao vivo", NotificationManager.IMPORTANCE_LOW)
            )
            b = Notification.Builder(this, "live")
        } else {
            b = Notification.Builder(this)
        }
        val n = b.setContentTitle("Ain Tradutor: modo ao vivo")
            .setContentText("Toque em Parar para desligar o botão flutuante")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Parar", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, n)
        }
    }

    @Suppress("DEPRECATION")
    private fun setupCapture(p: MediaProjection) {
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        val r = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2)
        reader = r
        vdisplay = p.createVirtualDisplay(
            "live", dm.widthPixels, dm.heightPixels, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler
        )
    }

    @Suppress("DEPRECATION")
    private fun showButton() {
        val density = resources.displayMetrics.density
        val size = (52 * density).toInt()
        val v = TextView(this)
        v.text = "Aa"
        v.setTextColor(Color.WHITE)
        v.gravity = Gravity.CENTER
        v.textSize = 14f
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(0xCC1976D2.toInt())
        v.background = bg

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val lp = WindowManager.LayoutParams(
            size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = (16 * density).toInt()
        lp.y = (160 * density).toInt()

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        v.setOnTouchListener { view, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        wm.updateViewLayout(view, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) capture()
                    true
                }
                else -> false
            }
        }
        wm.addView(v, lp)
        button = v
    }

    private fun capture() {
        if (busy) return
        if (!Paddle.isReady(applicationContext)) {
            toast("Baixe os modelos do OCR primeiro (menu ⋮ do app → OCR: modelos do PaddleOCR)")
            return
        }
        val img = reader?.acquireLatestImage()
        if (img == null) {
            toast("Ainda sem imagem da tela, toque de novo")
            return
        }
        busy = true
        var bmp: Bitmap? = null
        try {
            val plane = img.planes[0]
            val w = plane.rowStride / plane.pixelStride
            val full = Bitmap.createBitmap(w, img.height, Bitmap.Config.ARGB_8888)
            full.copyPixelsFromBuffer(plane.buffer)
            bmp = if (w != img.width) Bitmap.createBitmap(full, 0, 0, img.width, img.height) else full
        } catch (e: Throwable) {
        } finally {
            img.close()
        }
        if (bmp == null) {
            busy = false
            toast("Erro na captura da tela")
            return
        }
        val shot: Bitmap = bmp
        Thread {
            var found = IntArray(0)
            var err: String? = null
            try {
                Store.restore(applicationContext)
                val lines = Paddle.recognize(applicationContext, shot)
                found = Store.match(lines)
            } catch (e: Throwable) {
                err = e.message ?: e.javaClass.simpleName
            }
            handler.post {
                busy = false
                if (err != null) toast("O OCR falhou: $err") else openLive(found)
            }
        }.start()
    }

    private fun openLive(found: IntArray) {
        val i = Intent(this, LiveActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.putExtra("found", found)
        i.putExtra("start", if (found.isEmpty()) -1 else found.min())
        startActivity(i)
    }

    override fun onDestroy() {
        try {
            button?.let { wm.removeView(it) }
        } catch (e: Throwable) {
        }
        button = null
        vdisplay?.release()
        reader?.close()
        projection?.stop()
        Paddle.close()
        super.onDestroy()
    }
}

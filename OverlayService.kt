package com.wrhelper.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var bubble: TextView
    private lateinit var bubbleLp: WindowManager.LayoutParams
    private lateinit var panel: LinearLayout
    private lateinit var panelLp: WindowManager.LayoutParams
    private lateinit var web: WebView
    private var panelOpen = false
    private var typing = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        buildPanel()
        buildBubble()
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("wrfloat", "WR Float", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, OverlayService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, "wrfloat")
            .setContentTitle("WR Build Helper ativo")
            .setContentText("Toque em Parar para remover o balão")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Parar", stop).build())
            .setOngoing(true)
            .build()
        startForeground(1, n)
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun buildPanel() {
        val dm = resources.displayMetrics
        val w = min(dm.widthPixels - dp(16), dp(360))
        val h = min(dm.heightPixels - dp(24), dp(480))

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1E1B4B"))
            setPadding(dp(10), 0, dp(4), 0)
        }
        val title = TextView(this).apply {
            text = "WR Build Helper"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(36), 1f))

        fun hbtn(t: String, onClick: (TextView) -> Unit): TextView = TextView(this).apply {
            text = t
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setOnClickListener { onClick(this) }
        }
        val kb = hbtn("⌨") { v ->
            typing = !typing
            v.setTextColor(if (typing) Color.parseColor("#34D399") else Color.WHITE)
            if (typing) panelLp.flags = panelLp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            else panelLp.flags = panelLp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            wm.updateViewLayout(panel, panelLp)
        }
        val mn = hbtn("—") { closePanel() }
        val close = hbtn("✕") { stopSelf() }
        header.addView(kb, LinearLayout.LayoutParams(dp(40), dp(36)))
        header.addView(mn, LinearLayout.LayoutParams(dp(40), dp(36)))
        header.addView(close, LinearLayout.LayoutParams(dp(40), dp(36)))
        panel.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)))

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(false)
            setBackgroundColor(Color.parseColor("#0F172A"))
            loadUrl("file:///android_asset/index.html")
        }
        panel.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        panelLp = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildBubble() {
        val size = dp(52)
        bubble = TextView(this).apply {
            text = "WR"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E66D28D9"))
                setStroke(dp(2), Color.WHITE)
            }
        }
        bubbleLp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8); y = dp(120)
        }
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        bubble.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = bubbleLp.x; sy = bubbleLp.y; tx = e.rawX; ty = e.rawY; moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                        if (abs(dx) > slop || abs(dy) > slop) moved = true
                        if (moved) {
                            bubbleLp.x = sx + dx; bubbleLp.y = sy + dy
                            wm.updateViewLayout(bubble, bubbleLp)
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) togglePanel()
                }
                return true
            }
        })
        wm.addView(bubble, bubbleLp)
    }

    private fun togglePanel() = if (panelOpen) closePanel() else openPanel()

    private fun openPanel() {
        if (panelOpen) return
        val sw = resources.displayMetrics.widthPixels
        val bubbleOnLeft = bubbleLp.x + bubbleLp.width / 2 < sw / 2
        panelLp.x = if (bubbleOnLeft) sw - panelLp.width - dp(8) else dp(8)
        panelLp.y = dp(12)
        wm.addView(panel, panelLp)
        panelOpen = true
    }

    private fun closePanel() {
        if (!panelOpen) return
        wm.removeView(panel)
        panelOpen = false
    }

    override fun onDestroy() {
        if (panelOpen) wm.removeView(panel)
        wm.removeView(bubble)
        web.destroy()
        super.onDestroy()
    }
}

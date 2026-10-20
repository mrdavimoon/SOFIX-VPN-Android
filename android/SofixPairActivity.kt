package com.v2ray.ang.ui.sofix

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.ui.main.MainActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** SOFIX pairing; the upstream Xray core provides the native VPN tunnel. */
class SofixPairActivity : Activity() {
    companion object {
        private const val API = "https://lk.sofixconnection.ru"
        private const val PREF = "sofix_pair"
        private const val TOKEN = "token"
        fun paired(a: Activity): Boolean {
            val p = a.getSharedPreferences(PREF, MODE_PRIVATE)
            return p.getBoolean("pair_complete", false) && !p.getString(TOKEN, null).isNullOrBlank()
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)
    private lateinit var code: TextView
    private lateinit var info: TextView
    private lateinit var begin: Button
    private lateinit var open: Button
    private var telegramUrl: String? = null
    private var alive = false
    private var completed = false
    private val poll = object : Runnable {
        override fun run() {
            if (!alive || completed) return
            if (!token().isNullOrBlank()) check()
            handler.postDelayed(this, 2300L)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 16, 19)
        window.navigationBarColor = Color.rgb(9, 16, 19)
        fun rounded(color: Int, radius: Float = 22f) = GradientDrawable().apply {
            cornerRadius = radius * resources.displayMetrics.density
            setColor(color)
        }
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((26*dp).toInt(), (48*dp).toInt(), (26*dp).toInt(), (22*dp).toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(17, 43, 46), Color.rgb(7, 17, 21))
            )
        }
        fun label(s: String, size: Float = 16f, col: Int = Color.WHITE) = TextView(this).apply {
            text = s
            textSize = size
            setTextColor(col)
            setPadding(0, (13*dp).toInt(), 0, (13*dp).toInt())
        }
        fun btn(s: String, action: () -> Unit) = Button(this).apply {
            text = s
            setTextColor(Color.rgb(5, 25, 21))
            background = rounded(Color.rgb(154, 240, 204), 17f)
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { action() }
        }
        root.addView(label("SOFIX  /  VPN", 32f, Color.rgb(167, 245, 210)).apply {
            setTypeface(null, Typeface.BOLD)
            letterSpacing = .09f
        })
        root.addView(label("Подключите телефон", 24f).apply { setTypeface(null, Typeface.BOLD) })
        root.addView(label(
            "Откройте SOFIX в Telegram и подтвердите привязку. После этого приложение самостоятельно получит VPN-подписку.", 15f,
            Color.rgb(172, 200, 198)
        ))
        code = label("", 38f, Color.rgb(169, 255, 215)).apply {
            gravity = android.view.Gravity.CENTER
            letterSpacing = .18f
            setTypeface(null, Typeface.BOLD)
            background = rounded(Color.rgb(19, 55, 47), 18f)
            setPadding((14*dp).toInt(), (19*dp).toInt(), (14*dp).toInt(), (19*dp).toInt())
        }
        root.addView(code, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (14 * resources.displayMetrics.density).toInt() })
        info = label("Нажмите «Привязать устройство»."); root.addView(info)
        begin = btn("Привязать устройство") { startPairing() }; root.addView(begin)
        open = btn("Открыть SOFIX Mini App") {
            telegramUrl?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
        }
        open.visibility = View.GONE; root.addView(open)
        root.addView(btn("Начать заново") { prefs().edit().remove(TOKEN).remove("pair_complete")
            .remove("pair_pin").remove("telegram_url").apply(); startPairing() })
        setContentView(root)
        // Recover pending Telegram pairing after returning to the app or process restart.
        if (!token().isNullOrBlank()) {
            begin.visibility = View.GONE
            telegramUrl = prefs().getString("telegram_url", null)
            code.text = prefs().getString("pair_pin", "") ?: ""
            open.visibility = if (telegramUrl != null) View.VISIBLE else View.GONE
            info.text = "Проверяем подтверждение Telegram…"
        }
    }
    override fun onResume() { super.onResume(); alive = true; handler.removeCallbacks(poll); handler.post(poll) }
    override fun onPause() { alive = false; handler.removeCallbacks(poll); super.onPause() }
    private fun prefs() = getSharedPreferences(PREF, MODE_PRIVATE)
    private fun token() = prefs().getString(TOKEN, null)
    private fun ui(action: () -> Unit) = runOnUiThread { if (!isFinishing && !isDestroyed) action() }
    private fun work(action: () -> Unit) {
        if (!busy.compareAndSet(false,true)) return
        Thread { try { action() } finally { busy.set(false) } }.start()
    }
    private fun api(method: String, path: String, bearer: String?, data: JSONObject? = null): JSONObject {
        val conn = URL(API+path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000; conn.readTimeout = 10000; conn.requestMethod = method
            conn.setRequestProperty("Accept", "application/json")
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer "+bearer)
            if (data != null) {
                conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
            }
            val body = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (conn.responseCode !in 200..299) throw IllegalStateException(
                json.optString("detail", "Сервер вернул ошибку "+conn.responseCode))
            return json
        } finally { conn.disconnect() }
    }
    private fun startPairing() {
        info.text = "Готовим привязку…"; begin.isEnabled = false
        work {
            try {
                val deviceName = (Build.MANUFACTURER+" "+Build.MODEL).take(60)
                val r = api("POST","/mobile/api/pair/start",null, JSONObject().put("device_name",deviceName))
                telegramUrl = r.getString("telegram_url")
                prefs().edit()
                    .putString(TOKEN, r.getString("device_token"))
                    .putString("telegram_url", r.getString("telegram_url"))
                    .putString("pair_pin", r.getString("pin"))
                    .putBoolean("pair_complete", false).apply()
                ui {
                    code.text = r.getString("pin"); begin.visibility = View.GONE
                    open.visibility = View.VISIBLE; begin.isEnabled = true
                    info.text = "Введите этот код в SOFIX Mini App. Код действует 5 минут."
                }
            } catch (e: Exception) { ui { info.text = "Ошибка: "+e.message; begin.isEnabled = true } }
        }
    }
    private fun check() {
        val t=token() ?: return
        work {
            try {
                val r=api("GET","/mobile/api/me",t)
                when(r.optString("state")) {
                    "pending" -> ui { if (code.text.isEmpty()) info.text = "Ожидаем Telegram…" }
                    "setting_up" -> ui { info.text="Создаём отдельный VPN-ключ…" }
                    "no_subscription" -> ui { info.text="Телефон привязан. Сначала активируйте VPN." }
                    "ready" -> {
                        val url=r.getString("subscription_url")
                        if (!url.startsWith("https://")) throw IllegalStateException("Неверная ссылка VPN")
                        AngConfigManager.importBatchConfig(url,"",false)
                        prefs().edit().putBoolean("pair_complete", true)
                            .remove("pair_pin").remove("telegram_url").apply()
                        completed=true
                        ui {
                            info.text="Подписка получена!"
                            startActivity(Intent(this,MainActivity::class.java));finish()
                        }
                    }
                }
            } catch (e: Exception) {
                val msg=e.message.orEmpty()
                if (msg.contains("отвязано",true)||msg.contains("истёк",true)||msg.contains("авторизовано",true)) {
                    prefs().edit().remove(TOKEN).remove("pair_complete")
                        .remove("pair_pin").remove("telegram_url").apply()
                    ui { code.text="";open.visibility=View.GONE;begin.visibility=View.VISIBLE;info.text="Привязка недействительна. Начните заново." }
                } else ui { info.text="Проверяем соединение: "+msg }
            }
        }
    }
}

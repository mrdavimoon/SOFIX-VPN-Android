package com.v2ray.ang.ui.sofix

import android.app.Activity
import android.content.Intent
import android.graphics.Color
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
        fun paired(a: Activity) = !a.getSharedPreferences(PREF, MODE_PRIVATE)
            .getString(TOKEN, "").isNullOrEmpty()
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
            if (paired(this@SofixPairActivity)) check()
            handler.postDelayed(this, 2300L)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 16, 19)
        window.navigationBarColor = Color.rgb(9, 16, 19)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(34, 42, 34, 26)
            setBackgroundColor(Color.rgb(9, 16, 19))
        }
        fun label(s: String, size: Float = 16f, col: Int = Color.WHITE) = TextView(this).apply {
            text = s; textSize = size; setTextColor(col); setPadding(0, 12, 0, 12)
        }
        fun btn(s: String, action: () -> Unit) = Button(this).apply {
            text = s
            setTextColor(Color.rgb(5, 25, 21))
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(105, 224, 187)))
            setOnClickListener { action() }
        }
        root.addView(label("SOFIX VPN", 30f, Color.rgb(105, 224, 187)))
        root.addView(label("Привязка Android", 23f))
        root.addView(label("Привяжите телефон через Telegram. К одной подписке можно добавить до 3 устройств.", 15f))
        code = label("", 39f, Color.rgb(175, 255, 210)); root.addView(code)
        info = label("Нажмите «Привязать устройство»."); root.addView(info)
        begin = btn("Привязать устройство") { startPairing() }; root.addView(begin)
        open = btn("Открыть SOFIX Mini App") {
            telegramUrl?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
        }
        open.visibility = View.GONE; root.addView(open)
        root.addView(btn("Начать заново") { prefs().edit().remove(TOKEN).apply(); startPairing() })
        setContentView(root)
        if (paired(this)) { begin.visibility = View.GONE; info.text = "Проверяем привязку…" }
    }
    override fun onResume() { super.onResume(); alive = true; handler.post(poll) }
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
                prefs().edit().putString(TOKEN,r.getString("device_token")).apply()
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
                    prefs().edit().remove(TOKEN).apply()
                    ui { code.text="";open.visibility=View.GONE;begin.visibility=View.VISIBLE;info.text="Привязка недействительна. Начните заново." }
                } else ui { info.text="Проверяем соединение: "+msg }
            }
        }
    }
}

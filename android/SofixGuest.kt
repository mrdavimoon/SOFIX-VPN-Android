package com.v2ray.ang.ui.sofix

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.v2ray.ang.handler.AngConfigManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Anonymous first-run (no Telegram until the VPN is up).
 * Installation ID and short-lived bearer token live only in private app prefs.
 */
object SofixGuest {
    private const val BASE = "https://lk.sofixconnection.ru"
    private const val PREF = "sofix_guest"
    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun trialToken(c: Context): String? = prefs(c).getString("token", null)
    fun expiresAt(c: Context): Long = prefs(c).getLong("expires_at", 0L)
    fun trialLeft(c: Context): Long = (expiresAt(c) - System.currentTimeMillis()/1000L).coerceAtLeast(0)
    fun claimed(c: Context): Boolean = prefs(c).getBoolean("linked", false)
    fun verified(c: Context): Boolean = prefs(c).getBoolean("verified", false)
    fun started(c: Context) = !trialToken(c).isNullOrBlank()
    fun hasPaid(c: Context) = c.getSharedPreferences("sofix_pair",Context.MODE_PRIVATE)
        .getBoolean("pair_complete",false)


    private fun http(path: String, method: String, token: String?=null, payload: JSONObject?=null): JSONObject {
        val c = URL(BASE+path).openConnection() as HttpURLConnection
        try {
            c.connectTimeout=8000
            c.readTimeout=14000
            c.requestMethod=method
            c.setRequestProperty("Accept","application/json")
            if(token!=null)c.setRequestProperty("Authorization","Bearer "+token)
            if(payload!=null){
                c.doOutput=true
                c.setRequestProperty("Content-Type","application/json")
                c.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val response = (if(c.responseCode in 200..299)c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: "{}"
            val json = JSONObject(response)
            if(c.responseCode !in 200..299)
                throw IllegalStateException(json.optString("detail","Ошибка сервера "+c.responseCode))
            return json
        } finally { c.disconnect() }
    }

    /** Starts one 15-minute 150 MB trial per installation. Must not run on main thread. */
    fun startTrial(context: Context): JSONObject {
        val p=prefs(context)
        if (!p.getString("token",null).isNullOrBlank()) {
            throw IllegalStateException("Пробный доступ уже выдавался. Продолжите через Telegram.")
        }
        val install=p.getString("installation",null) ?: UUID.randomUUID().toString().also {
            p.edit().putString("installation",it).apply()
        }
        val device=(Build.MANUFACTURER+" "+Build.MODEL).trim().take(64)
        val data=JSONObject().put("installation_id",install).put("device_name",device)
        val response=http("/mobile/api/trial/start","POST",payload=data)
        val sub=response.getString("subscription_url")
        if(!sub.startsWith("https://"))throw IllegalStateException("Сервер не выдал VPN")
        val result=AngConfigManager.importBatchConfig(sub,"",false)
        // When imported once, a later import may deduplicate. Do not report success
        // merely from the returned count; actual VPN and internet are tested next.
        p.edit().putString("token",response.getString("device_token"))
            .putLong("expires_at",response.getLong("expires_at"))
            .putBoolean("linked",false).putBoolean("verified",false).apply()
        return response
    }

    fun trialStatus(c: Context): JSONObject {
        val token=trialToken(c) ?: throw IllegalStateException("Нет пробного доступа")
        val data=http("/mobile/api/trial/status","GET",token)
        val state=data.optString("state")
        val linked=data.optBoolean("linked") || state=="linked_trial" || state=="linked_expired" || state=="ready"
        prefs(c).edit().putBoolean("linked",linked).apply()
        if (state=="ready") {
            val url=data.optString("subscription_url")
            if(url.startsWith("https://")) {
                AngConfigManager.importBatchConfig(url,"",false)
                c.getSharedPreferences("sofix_pair",Context.MODE_PRIVATE).edit()
                    .putString("token",token).putBoolean("pair_complete",true).apply()
            }
        }
        return data
    }

    fun markVerified(c: Context) { prefs(c).edit().putBoolean("verified",true).apply() }

    fun beginLink(c: Context): String {
        val token=trialToken(c) ?: throw IllegalStateException("Сначала включите пробный доступ")
        return http("/mobile/api/trial/link/start","POST",token).getString("telegram_url")
    }

    /** First confirm actual Android VPN transport, then reach Telegram over HTTPS. */
    fun checkVpnAndTelegram(c: Context): Boolean {
        val cm=c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap=cm.getNetworkCapabilities(cm.activeNetwork)
        if (cap==null || !cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false
        return try {
            val test=URL("https://telegram.org").openConnection() as HttpURLConnection
            try {
                test.connectTimeout=7000
                test.readTimeout=7000
                test.instanceFollowRedirects=true
                test.setRequestProperty("User-Agent","SOFIX-VPN-connectivity/1")
                val code=test.responseCode
                code in 200..499
            } finally {test.disconnect()}
        } catch (_:Exception) {false}
    }
}

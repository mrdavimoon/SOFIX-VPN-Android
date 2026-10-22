package com.v2ray.ang.ui.sofix

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Short-lived unauthenticated trial. No Telegram required until after VPN is working. */
class SofixApiException(val statusCode:Int, message:String):IllegalStateException(message)

object SofixTrialManager {
    private const val BASE = "https://lk.sofixconnection.ru"
    private const val PREF = "sofix_trial"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun token(ctx: Context): String? = prefs(ctx).getString("trial_token", null)
    fun expiresAt(ctx: Context): Long = prefs(ctx).getLong("expires_at",0)
    fun linked(ctx: Context): Boolean = prefs(ctx).getBoolean("linked",false)
    fun paid(ctx: Context): Boolean = prefs(ctx).getBoolean("paid",false)
    fun linkUrl(ctx: Context): String? = prefs(ctx).getString("link_url",null)
    fun markLinked(ctx: Context) { prefs(ctx).edit().putBoolean("linked",true).apply() }
    fun markPaid(ctx: Context) { prefs(ctx).edit().putBoolean("paid",true).apply() }
    fun clearLinkUrl(ctx: Context) { prefs(ctx).edit().remove("link_url").apply() }

    private fun installId(ctx:Context):String {
        val p=prefs(ctx)
        val prev=p.getString("install_id",null)
        if(prev!=null)return prev
        val next=UUID.randomUUID().toString()
        p.edit().putString("install_id",next).commit()
        return next
    }
    fun api(method:String,path:String,token:String?=null,body:JSONObject?=null):JSONObject {
        val conn=URL(BASE+path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout=10000;conn.readTimeout=12000;conn.requestMethod=method
            conn.setRequestProperty("Accept","application/json")
            if(token!=null)conn.setRequestProperty("Authorization","Bearer "+token)
            if(body!=null) {
                conn.doOutput=true
                conn.setRequestProperty("Content-Type","application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code=conn.responseCode
            val stream=if(code in 200..299)conn.inputStream else conn.errorStream
            val text=stream?.bufferedReader()?.use{it.readText()} ?: "{}"
            val json=JSONObject(text)
            if(code !in 200..299)
                throw SofixApiException(code,json.optString("detail","HTTP "+code))
            return json
        }finally{conn.disconnect()}
    }

    fun start(ctx:Context):JSONObject {
        if(token(ctx)!=null) return api("GET","/mobile/api/trial/config",token(ctx))
        val name=(Build.MANUFACTURER+" "+Build.MODEL).trim().take(60)
        val request=JSONObject().put("installation_id",installId(ctx)).put("device_name",name)
        val result=api("POST","/mobile/api/trial/start",body=request)
        prefs(ctx).edit()
            .putString("trial_token",result.getString("device_token"))
            .putLong("expires_at",result.getLong("expires_at"))
            .apply()
        return result
    }

    fun status(ctx:Context):JSONObject? {
        val token=token(ctx) ?: return null
        return api("GET","/mobile/api/trial/status",token)
    }

    fun connectivityCheck(): Boolean {
        fun reachable(address:String):Boolean {
            val conn=(URL(address).openConnection() as HttpURLConnection)
            try {
                conn.connectTimeout=5000
                conn.readTimeout=6500
                conn.instanceFollowRedirects=false
                conn.setRequestProperty("User-Agent","SOFIX-VPN/1.0")
                val code=conn.responseCode
                return code in 200..399
            }catch (_:Exception){ return false }
            finally { conn.disconnect() }
        }
        return reachable("https://www.gstatic.com/generate_204") &&
            reachable("https://t.me/SofixVpnBot")
    }

    fun beginLink(ctx:Context):String {
        val previous=linkUrl(ctx)
        val deadline=prefs(ctx).getLong("link_until",0)
        if(previous!=null&&System.currentTimeMillis()/1000 < deadline) return previous
        val result=api("POST","/mobile/api/trial/link/start",token(ctx) ?: error("Нет пробного подключения"))
        val url=result.getString("telegram_url")
        prefs(ctx).edit()
            .putString("link_url",url)
            .putLong("link_until",System.currentTimeMillis()/1000+result.optLong("expires_seconds",600))
            .apply()
        return url
    }
}

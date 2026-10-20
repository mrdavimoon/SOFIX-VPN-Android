package com.v2ray.ang.ui.sofix

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Separate rules channel, pinned Ed25519 signature, last-known-good.
 * The server can publish only domain classifications, never device/subscription keys.
 */
object SofixRuleUpdates {
    private const val PREF="sofix_rule_updates"
    private const val URL_RULES =
        "https://raw.githubusercontent.com/mrdavimoon/SOFIX-VPN-Android/main/rules/manifest.json"
    private const val PUB="MCowBQYDK2VwAyEAZctsxIaZi/5OCzVsz2tged0PEjJyEVWqGD7/M0u192c="
    private val fallbackBlocked=setOf("youtube.com","instagram.com","facebook.com","twitter.com")
    fun blocked(c:Context):List<String> = stored(c,"blocked_domains",fallbackBlocked)
    fun ru(c:Context):List<String> = stored(c,"ru_domains",emptySet())
    fun version(c:Context):Int = c.getSharedPreferences(PREF,0).getInt("version",0)
    fun lastChecked(c:Context):Long = c.getSharedPreferences(PREF,0).getLong("checked",0L)
    private fun stored(c:Context,key:String,fallback:Set<String>):List<String> {
        val raw=c.getSharedPreferences(PREF,0).getString("payload",null) ?: return fallback.sorted()
        return try {
            val arr=JSONObject(raw).getJSONArray(key)
            (0 until arr.length()).map{arr.getString(it)}.sorted()
        }catch (_:Exception){fallback.sorted()}
    }
    private fun validate(array:JSONArray):List<String> {
        require(array.length()<=16000)
        return (0 until array.length()).map {
            val item=array.getString(it)
            require(item.length in 4..253)
            require(SofixRouting.canonical(item)==item)
            item
        }.distinct().also {require(it.size==array.length())}
    }
    fun verifyEnvelope(envelope:JSONObject):JSONObject {
        val payload=envelope.getString("payload")
        require(payload.toByteArray(Charsets.UTF_8).size<1_800_000)
        val sig=Base64.decode(envelope.getString("signature"),Base64.NO_WRAP)
        require(sig.size==64)
        val key=KeyFactory.getInstance("Ed25519").generatePublic(
            X509EncodedKeySpec(Base64.decode(PUB,Base64.DEFAULT)))
        val verifier=Signature.getInstance("Ed25519")
        verifier.initVerify(key)
        verifier.update(payload.toByteArray(Charsets.UTF_8))
        require(verifier.verify(sig)){"Неверная подпись списка"}
        val parsed=JSONObject(payload)
        require(parsed.getInt("format")==1)
        require(parsed.getInt("version")>=1)
        require(parsed.getLong("expires_at")>System.currentTimeMillis()/1000)
        validate(parsed.getJSONArray("blocked_domains"))
        validate(parsed.getJSONArray("ru_domains"))
        return parsed
    }

    /** Called on IO thread; no automatic fallback to an unsigned remote list. */
    fun refresh(c:Context, force:Boolean=false):String {
        val prefs=c.getSharedPreferences(PREF,0)
        val now=System.currentTimeMillis()/1000
        if(!force && now-lastChecked(c)<86400) return "Правила уже проверены"
        val conn=URL(URL_RULES).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout=7000;conn.readTimeout=9000
            conn.setRequestProperty("Accept","application/json")
            require(conn.responseCode==200)
            val bytes=conn.inputStream.use { stream ->
                val out=java.io.ByteArrayOutputStream()
                val buf=ByteArray(8192)
                while(true) {
                    val count=stream.read(buf)
                    if(count<0)break
                    require(out.size()+count<=1_800_000)
                    out.write(buf,0,count)
                }
                out.toByteArray()
            }
            require(bytes.size<=1_800_000)
            val payload=verifyEnvelope(JSONObject(String(bytes,Charsets.UTF_8)))
            val remoteVersion=payload.getInt("version")
            require(remoteVersion>=version(c)){"Версия списка устарела"}
            // Store payload atomically; invalid responses never modify LKG.
            prefs.edit().putString("payload",payload.toString())
                .putInt("version",remoteVersion).putLong("checked",now).commit()
            return "Правила обновлены · версия $remoteVersion"
        }finally{conn.disconnect()}
    }
}

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
    private val fallbackBlocked=setOf(
        "youtube.com","youtu.be","googlevideo.com","ytimg.com",
        "instagram.com","cdninstagram.com","facebook.com","fbcdn.net",
        "telegram.org","t.me","telegram.me","telesco.pe",
        "whatsapp.com","whatsapp.net","wa.me",
        "tiktok.com","tiktokcdn.com","tiktokv.com",
        "discord.com","discord.gg","discordapp.com",
        "claude.ai","anthropic.com","twitter.com","x.com"
    )
    fun blocked(c:Context):List<String> = stored(c,"blocked_domains",fallbackBlocked)
    fun ru(c:Context):List<String> = stored(c,"ru_domains",emptySet())
    fun proxyRules(c:Context):List<String> =
        storedV3(c,"proxy_rules",SofixHybridDefaults.proxyRules)
    fun directRules(c:Context):List<String> =
        storedV3(c,"direct_rules",SofixHybridDefaults.directRules)
    fun proxyIps(c:Context):List<String> =
        storedV3(c,"proxy_ips",SofixHybridDefaults.proxyIps)
    fun directIps(c:Context):List<String> =
        storedV3(c,"direct_ips",SofixHybridDefaults.directIps)
    fun version(c:Context):Int = c.getSharedPreferences(PREF,0).getInt("version",0)
    fun lastChecked(c:Context):Long = c.getSharedPreferences(PREF,0).getLong("checked",0L)
    private fun stored(c:Context,key:String,fallback:Set<String>):List<String> {
        val raw=c.getSharedPreferences(PREF,0).getString("payload",null) ?: return fallback.sorted()
        return try {
            val arr=JSONObject(raw).getJSONArray(key)
            (0 until arr.length()).map{arr.getString(it)}.sorted()
        }catch (_:Exception){fallback.sorted()}
    }
    private fun storedV3(c:Context,key:String,fallback:List<String>):List<String> {
        val raw=c.getSharedPreferences(PREF,0).getString("payload",null) ?: return fallback
        return try {
            val payload=JSONObject(raw)
            if(payload.optInt("version")<3)return fallback
            val arr=payload.getJSONArray(key)
            (0 until arr.length()).map { arr.getString(it) }
        }catch (_:Exception){ fallback }
    }
    private fun validateRouteRules(array:JSONArray):List<String> {
        require(array.length() in 1..16000)
        val rules=(0 until array.length()).map { array.getString(it) }
        require(rules.distinct().size==rules.size)
        for(rule in rules) {
            val type=rule.substringBefore(':')
            val host=rule.substringAfter(':',"")
            require(type=="domain" || type=="full")
            require(SofixRouting.canonical(host)==host)
        }
        return rules
    }
    private fun validateCidrs(array:JSONArray):List<String> {
        require(array.length() in 1..1000)
        val ranges=(0 until array.length()).map { array.getString(it) }
        require(ranges.distinct().size==ranges.size)
        for(cidr in ranges) {
            require(cidr.length in 3..65)
            val host=cidr.substringBefore('/')
            require(host.contains('.') || host.contains(':'))
            require(host.all { it in "0123456789abcdefABCDEF.:" })
            val bits=java.net.InetAddress.getByName(host).address.size*8
            require(cidr.indexOf('/')<0 || cidr.substringAfter('/').toIntOrNull()
                ?.let { it in 0..bits } == true)
        }
        return ranges
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
        if(parsed.getInt("version")>=3) {
            val proxy=validateRouteRules(parsed.getJSONArray("proxy_rules"))
            val direct=validateRouteRules(parsed.getJSONArray("direct_rules"))
            require(proxy.size>=80)
            require(proxy.toSet().intersect(direct.toSet()).isEmpty())
            require(validateCidrs(parsed.getJSONArray("proxy_ips")).size>=20)
            validateCidrs(parsed.getJSONArray("direct_ips"))
        }
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

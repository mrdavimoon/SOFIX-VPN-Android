package com.v2ray.ang.ui.sofix

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.handler.MmkvManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.IDN
import java.net.URI

/** Local source of truth. SOFIX-managed routing is never stored in legacy expert rules. */
object SofixRouting {
    private const val PREFS = "sofix_routing_v1"
    const val SMART = "smart"
    const val ALL = "all"
    const val SELECTED = "selected"
    private val allowedModes = setOf(SMART, ALL, SELECTED)

    fun mode(c: Context): String = c.getSharedPreferences(PREFS,0)
        .getString("mode",SMART)?.takeIf { it in allowedModes } ?: SMART
    fun setMode(c: Context, mode:String) {
        require(mode in allowedModes)
        c.getSharedPreferences(PREFS,0).edit().putString("mode",mode).apply()
    }
    private fun key(proxy:Boolean) = if(proxy)"always_proxy" else "always_direct"
    fun entries(c:Context, proxy:Boolean):List<String> =
        c.getSharedPreferences(PREFS,0).getStringSet(key(proxy), emptySet())
            .orEmpty().sortedWith(compareByDescending<String>{it.length}.thenBy{it}).toList()

    /** One domain and all children. No broad TLD wildcard is permitted. */
    fun canonical(input:String):String {
        val raw=input.trim()
        require(raw.length in 4..400 && raw.none{it.isWhitespace() || it.isISOControl()}) {
            "Введите адрес сайта без пробелов"
        }
        val parsed=try {
            URI(if("://" in raw)raw else "https://$raw")
        }catch (_:Exception){throw IllegalArgumentException("Неправильный адрес сайта")}
        require(parsed.scheme == "https" || parsed.scheme == "http")
        require(parsed.userInfo==null)
        var host=(parsed.host ?: parsed.rawAuthority?.substringBeforeLast(':'))
            ?.trimEnd('.')?.lowercase()
            ?: throw IllegalArgumentException("Неправильный адрес")
        host=try { IDN.toASCII(host,IDN.USE_STD3_ASCII_RULES).lowercase() }
        catch (_:Exception){throw IllegalArgumentException("Неправильный домен")}
        val parts=host.split('.')
        require(host.length in 4..253 && parts.size>=2 && parts.all {it.length in 1..63})
        require(!host.contains('*') && !host.contains(':') &&
            !Regex("^(?:\\d+\\.){3}\\d+$").matches(host))
        require(parts.last().length>=2 && parts.last().any{it.isLetter()})
        require(parts.size>=2 && !((parts.size==2) && parts[0] in setOf("co","com","org","net") &&
            parts[1] in setOf("uk","ru","jp","au")))
        return host
    }
    fun mutate(c:Context, host:String, proxy:Boolean, remove:Boolean=false):String {
        val domain=canonical(host)
        val p=c.getSharedPreferences(PREFS,0)
        val own=p.getStringSet(key(proxy),emptySet()).orEmpty().toMutableSet()
        val opposite=p.getStringSet(key(!proxy),emptySet()).orEmpty().toMutableSet()
        if(remove)own.remove(domain) else {
            // Atomic two-list edit: a domain may be in exactly one list.
            opposite.remove(domain)
            own.add(domain)
        }
        require(own.size+opposite.size<=250) { "Допускается до 250 личных правил" }
        p.edit().putStringSet(key(proxy),own).putStringSet(key(!proxy),opposite).commit()
        return domain
    }
    private fun match(host:String, pattern:String) = host==pattern || host.endsWith(".$pattern")
    private fun mostSpecific(host:String, direct:List<String>, proxy:List<String>):Pair<String,String>? {
        val combined=direct.map{it to "direct"}+proxy.map{it to "proxy"}
        return combined.filter{match(host,it.first)}
            .sortedWith(compareByDescending<Pair<String,String>>{it.first.length}.thenBy{it.first})
            .firstOrNull()?.let { it.second to "Личное правило: "+it.first }
    }
    fun explain(c:Context, text:String):Pair<String,String> {
        val host=canonical(text)
        mostSpecific(host,entries(c,false),entries(c,true))?.let {return it}
        if(mode(c)==ALL)return "proxy" to "Весь интернет через VPN"
        if(mode(c)==SELECTED)return "direct" to "Выбраны только отдельные сайты"
        val blocked=SofixRuleUpdates.blocked(c)
        if(blocked.any{match(host,it)})return "proxy" to "Список ограниченных ресурсов"
        if(host.endsWith(".ru") || host.endsWith(".xn--p1ai") || SofixRuleUpdates.ru(c).any{match(host,it)})
            return "direct" to "Российский шаблон"
        return "proxy" to "Неизвестный адрес — через VPN"
    }

    /**
     * Compiles priority rules directly into Xray. DNS-specific inbounds are kept
     * ahead of routing; default direct for selected mode must remain *after* DNS.
     * Returns false if an expert legacy ruleset needs explicit migration.
     */
    fun apply(c: Context, config:V2rayConfig):Boolean {
        // No silent rewriting of user-defined advanced routing.
        val expert=MmkvManager.decodeRoutingRulesets().orEmpty().filter{it.enabled}
        if(expert.isNotEmpty() && !c.getSharedPreferences(PREFS,0).getBoolean("expert_confirmed",false))
            return false

        val direct=entries(c,false)
        val proxy=entries(c,true)
        val rules=mutableListOf<V2rayConfig.RoutingBean.RulesBean>()
        fun add(tag:String,domains:List<String>) {
            if(domains.isNotEmpty())rules.add(V2rayConfig.RoutingBean.RulesBean(
                domain=domains, outboundTag=tag))
        }
        // LAN is internal and never forwarded by proxy routing.
        rules.add(V2rayConfig.RoutingBean.RulesBean(
            ip=listOf("geoip:private"),outboundTag=AppConfig.TAG_DIRECT))
        // More specific user exceptions first. Use exact host + suffix matching.
        val manual=(direct.map{it to AppConfig.TAG_DIRECT}+proxy.map{it to AppConfig.TAG_PROXY})
            .sortedWith(compareByDescending<Pair<String,String>>{it.first.length}.thenBy{it.first})
        for((domain,tag) in manual)add(tag,listOf("full:$domain","domain:$domain"))
        if(mode(c)==SMART) {
            add(AppConfig.TAG_PROXY,SofixRuleUpdates.blocked(c).map{"domain:$it"})
            add(AppConfig.TAG_DIRECT,listOf("regexp:.*\\.(ru|xn--p1ai)$","geosite:category-ru")+
                SofixRuleUpdates.ru(c).map{"domain:$it"})
        }
        // First insert high-priority domain/IP rules; they do not match dns-out
        // inbounds by themselves. Existing Xray DNS rules must remain intact.
        config.routing.rules.addAll(0,rules)
        // For "selected" we must default to direct only AFTER all DNS routes.
        if(mode(c)==SELECTED) {
            config.routing.rules.add(V2rayConfig.RoutingBean.RulesBean(
                domain=listOf("regexp:.*"),outboundTag=AppConfig.TAG_DIRECT))
            config.routing.rules.add(V2rayConfig.RoutingBean.RulesBean(
                ip=listOf("0.0.0.0/0","::/0"),outboundTag=AppConfig.TAG_DIRECT))
        }
        // Xray default outbound is proxy in other modes.
        config.routing.domainStrategy="AsIs"
        return true
    }
}

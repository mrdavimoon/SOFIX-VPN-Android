package com.v2ray.ang.ui.sofix

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.util.JsonUtil
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
    private fun matchRule(host:String, rule:String):Boolean = when {
        rule.startsWith("full:") -> host==rule.removePrefix("full:")
        rule.startsWith("domain:") -> match(host,rule.removePrefix("domain:"))
        else -> false
    }
    fun explain(c:Context, text:String):Pair<String,String> {
        val host=canonical(text)
        mostSpecific(host,entries(c,false),entries(c,true))?.let {return it}
        if(mode(c)==ALL)return "proxy" to "Весь интернет через VPN"
        if(mode(c)==SELECTED)return "direct" to "Выбраны только отдельные сайты"
        if(SofixRuleUpdates.proxyRules(c).any{matchRule(host,it)})
            return "proxy" to "Ограниченный ресурс SOFIX"
        if(SofixRuleUpdates.directRules(c).any{matchRule(host,it)})
            return "direct" to "Прямой выход с IP телефона"
        return "direct" to "Не ограничен — напрямую с IP телефона"
    }

    /**
     * v2rayNG ships a non-empty WHITE rule set to every clean installation.
     * It is not an expert customization and must NOT disable SOFIX routing.
     * Compare semantics to the actual bundled asset; never match just by size.
     */
    fun isUntouchedFactoryPreset(c:Context, items:List<RulesetItem>):Boolean {
        if (items.isEmpty()) return false
        return try {
            val asset=c.assets.open("custom_routing_white")
                .bufferedReader().use { it.readText() }
            val expected=JsonUtil.fromJsonSafe(asset,Array<RulesetItem>::class.java)
                ?.toList().orEmpty()
            items.size==expected.size && items.zip(expected).all { (a,b) ->
                a.ip==b.ip && a.domain==b.domain && a.process==b.process &&
                a.outboundTag==b.outboundTag && a.port==b.port &&
                a.network==b.network && a.protocol==b.protocol &&
                a.enabled==b.enabled && a.locked==b.locked
            }
        } catch (_:Exception){false}
    }

    /** Whether SOFIX may replace the legacy routing rules at runtime. */
    fun managedActive(c:Context):Boolean {
        val experts=MmkvManager.decodeRoutingRulesets().orEmpty().filter { it.enabled }
        if(experts.isEmpty() || isUntouchedFactoryPreset(c,experts))return true
        return c.getSharedPreferences(PREFS,0).getBoolean("expert_confirmed",false)
    }

    fun customRulesRequireConsent(c:Context):Boolean = !managedActive(c)

    /** Never delete the old v2rayNG entries: they remain available for rollback. */
    fun enableManagedWithLegacyBackup(c:Context) {
        val original=MmkvManager.decodeRoutingRulesets().orEmpty()
        val prefs=c.getSharedPreferences(PREFS,0)
        val backup=JsonUtil.toJson(original)
        check(prefs.edit().putString("expert_backup",backup)
            .putBoolean("expert_confirmed",true).commit())
    }

    fun canRestoreExpert(c:Context):Boolean {
        val prefs=c.getSharedPreferences(PREFS,0)
        return prefs.getBoolean("expert_confirmed",false) &&
            prefs.contains("expert_backup")
    }

    fun restoreLegacyRules(c:Context) {
        check(c.getSharedPreferences(PREFS,0).edit()
            .putBoolean("expert_confirmed",false).commit())
    }

    /**
     * Compiles priority rules directly into Xray. DNS-specific inbounds are kept
     * ahead of routing; default direct for selected mode must remain *after* DNS.
     * Returns false if an expert legacy ruleset needs explicit migration.
     */
    fun apply(c: Context, config:V2rayConfig):Boolean {
        // No silent rewriting of user-defined advanced routing.
        if(!managedActive(c))return false

        val direct=entries(c,false)
        val proxy=entries(c,true)
        val rules=mutableListOf<V2rayConfig.RoutingBean.RulesBean>()
        fun add(tag:String,domains:List<String>) {
            if(domains.isNotEmpty())rules.add(V2rayConfig.RoutingBean.RulesBean(
                domain=domains, outboundTag=tag))
        }
        fun addIp(tag:String,ips:List<String>) {
            if(ips.isNotEmpty())rules.add(V2rayConfig.RoutingBean.RulesBean(
                ip=ips, outboundTag=tag))
        }
        // LAN is internal and never forwarded by proxy routing.
        rules.add(V2rayConfig.RoutingBean.RulesBean(
            ip=listOf("geoip:private"),outboundTag=AppConfig.TAG_DIRECT))
        add(AppConfig.TAG_DIRECT,listOf("geosite:private"))
        // More specific user exceptions first. Use exact host + suffix matching.
        val manual=(direct.map{it to AppConfig.TAG_DIRECT}+proxy.map{it to AppConfig.TAG_PROXY})
            .sortedWith(compareByDescending<Pair<String,String>>{it.first.length}.thenBy{it.first})
        for((domain,tag) in manual)add(tag,listOf("full:$domain","domain:$domain"))
        if(mode(c)==SMART) {
            // Follow the same approved Hybrid B policy as Happ (GlobalProxy=false):
            // only the explicit restricted domain and CIDR lists use the VPN.
            add(AppConfig.TAG_PROXY,SofixRuleUpdates.proxyRules(c))
            add(AppConfig.TAG_DIRECT,SofixRuleUpdates.directRules(c))
            addIp(AppConfig.TAG_PROXY,SofixRuleUpdates.proxyIps(c))
            addIp(AppConfig.TAG_DIRECT,SofixRuleUpdates.directIps(c))
            // Mobile applications often connect to resolved IPs. Recover HTTP/TLS
            // hostnames before comparing routes, including on the TUN inbound.
            config.inbounds.filter { it.tag=="tun" || it.protocol=="socks" }.forEach {
                it.sniffing?.enabled=true
                it.sniffing?.routeOnly=false
                it.sniffing?.destOverride?.let { values ->
                    if("http" !in values) values.add("http")
                    if("tls" !in values) values.add("tls")
                }
            }
        }
        // First insert high-priority domain/IP rules; they do not match dns-out
        // inbounds by themselves. Existing Xray DNS rules must remain intact.
        val dnsCount=config.routing.rules.takeWhile {
            it.outboundTag=="dns-out" && it.port=="53"
        }.size
        config.routing.rules.addAll(dnsCount,rules)
        // Must be *after* every pre-existing DNS rule; otherwise wildcard DIRECT
        // would silently break the DNS interception on Android VPN TUN.
        if(mode(c)==SMART || mode(c)==SELECTED) {
            config.routing.rules.add(V2rayConfig.RoutingBean.RulesBean(
                network="tcp,udp",outboundTag=AppConfig.TAG_DIRECT))
        }
        // Mirror Happ IPIfNonMatch: IP-only clients are matched against CIDRs.
        config.routing.domainStrategy=if(mode(c)==SMART)"IPIfNonMatch" else "AsIs"
        return true
    }
}

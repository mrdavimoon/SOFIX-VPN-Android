package com.v2ray.ang.ui.sofix

import android.content.Context
import android.os.Build
import org.json.JSONObject

/**
 * Paid-account recovery is NOT a trial. A phone may only receive a new paid
 * key after the owner approves a six-digit challenge inside Telegram Mini App.
 *
 * Keep the pending token separate. A failed or unapproved challenge must never
 * replace a previously working credential.
 */
object SofixPaidPair {
    private const val PREF = "sofix_paid_pair_pending"
    private fun prefs(c:Context) = c.getSharedPreferences(PREF,Context.MODE_PRIVATE)

    fun pendingToken(c:Context):String? =
        prefs(c).getString("device_token",null)?.takeIf{
            it.length==43 && prefs(c).getString("mode",null)=="telegram"
        }
    fun telegramUrl(c:Context):String? = prefs(c).getString("telegram_url",null)
    fun isPending(c:Context):Boolean = pendingToken(c)!=null

    fun start(c:Context):String {
        val name=(Build.MANUFACTURER+" "+Build.MODEL).trim().take(64)
        val body=JSONObject().put("device_name",name)
            .put("confirmation_mode","telegram")
        val result=SofixTrialManager.api("POST","/mobile/api/pair/start",body=body)
        val token=result.getString("device_token")
        val challengeUrl=result.getString("telegram_url")
        require(token.matches(Regex("[A-Za-z0-9_-]{43}")))
        require(challengeUrl.startsWith("https://t.me/SofixVpnBot?startapp=device_"))
        check(prefs(c).edit()
            .putString("device_token",token)
            .remove("pin")
            .putString("mode","telegram")
            .putString("telegram_url",challengeUrl)
            .putLong("created_at",System.currentTimeMillis()/1000L)
            .commit())
        return challengeUrl
    }

    fun check(c:Context):JSONObject {
        val token=pendingToken(c) ?: throw IllegalStateException("Нет запроса привязки")
        return SofixTrialManager.api("GET","/mobile/api/me",token)
    }

    /** Promote only AFTER authenticated /mobile/api/me returns ready. */
    fun complete(c:Context) {
        val token=pendingToken(c) ?: error("Нет подтверждённого устройства")
        SofixLegacyPair.saveConfirmedToken(c,token)
        clear(c)
    }

    fun clear(c:Context) { prefs(c).edit().clear().commit() }
}

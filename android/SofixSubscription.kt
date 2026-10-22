package com.v2ray.ang.ui.sofix

import android.content.Context
import org.json.JSONObject
import kotlin.math.ceil

/** Subscription status comes from authenticated device API, never UI guesses. */
object SofixSubscription {
    private const val PREF="sofix_sub_display"
    private fun prefs(c:Context)=c.getSharedPreferences(PREF,Context.MODE_PRIVATE)
    fun fetch(c:Context):String {
        val paid=SofixLegacyPair.token(c)
        val trial=SofixTrialManager.token(c)
        if(paid==null && trial==null)
            return "Нет подписки · 15 минут бесплатно"
        // A paid device must override an old (possibly expired) trial.
        // Only fall back when the old paid token has actually been revoked.
        val data=if(paid!=null) {
            try {
                SofixTrialManager.api("GET","/mobile/api/me",paid)
            }catch(e:SofixApiException){
                if(e.statusCode in listOf(401,403) && trial!=null)
                    SofixTrialManager.api("GET","/mobile/api/trial/status",trial)
                else throw e
            }
        }else SofixTrialManager.api("GET","/mobile/api/trial/status",trial)
        val state=data.optString("state","unknown")
        val seconds=data.optLong("expires_at",0)-System.currentTimeMillis()/1000L
        val phrase=when(state){
            "ready"->if(seconds>0)"Активна · "+format(seconds)
                else "VPN работает · проверьте срок"
            "linked_trial","trial","active"->if(seconds>0)"Пробный доступ · "+format(seconds)
                else "Пробный доступ закончился"
            "linked_expired","expired"->"Подписка закончилась"
            "no_subscription"->"Нет подписки · оформите в Telegram"
            "setting_up"->"Настраиваем подписку…"
            else->"Проверяем подписку…"
        }
        prefs(c).edit().putString("last",phrase)
            .putLong("checked",System.currentTimeMillis()/1000L).apply()
        return phrase
    }
    fun cached(c:Context):String =
        prefs(c).getString("last","Проверяем подписку…") ?: "Проверяем подписку…"
    fun stale(c:Context):Boolean =
        (System.currentTimeMillis()/1000L - prefs(c).getLong("checked",0L))>300
    private fun format(sec:Long):String {
        if(sec<3600)return (sec/60).coerceAtLeast(1).toString()+" мин"
        if(sec<86400)return (sec/3600).toString()+" ч"
        return ceil(sec/86400.0).toInt().toString()+" дн"
    }
}

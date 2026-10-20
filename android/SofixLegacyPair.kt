package com.v2ray.ang.ui.sofix

import android.content.Context

/**
 * Legacy pairing (0.1–0.4): SofixPairActivity saved a device token after
 * /mobile/api/pair/start, but never wrote the pair_complete flag.
 * Do not require a flag absent from historical installs.
 *
 * A saved token is only an indication that the user can retry device status;
 * actual entitlement and subscription URL are verified by /mobile/api/me.
 */
object SofixLegacyPair {
    private const val PREF = "sofix_pair"
    fun token(context: Context): String? =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("token", null)
            ?.takeIf { it.isNotBlank() }

    fun paired(context: Context): Boolean = token(context) != null

    fun confirmed(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean("pair_complete", false)

    fun markConfirmed(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean("pair_complete", true).apply()
    }
}

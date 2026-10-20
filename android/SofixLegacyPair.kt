package com.v2ray.ang.ui.sofix

import android.content.Context

/** Read-only compatibility with SOFIX 0.1–0.4 pairing, without displaying PIN UI. */
object SofixLegacyPair {
    fun paired(context: Context): Boolean {
        val p=context.getSharedPreferences("sofix_pair",Context.MODE_PRIVATE)
        return p.getBoolean("pair_complete",false) &&
            !p.getString("token",null).isNullOrBlank()
    }
}

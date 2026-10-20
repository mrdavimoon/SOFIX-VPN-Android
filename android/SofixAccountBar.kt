package com.v2ray.ang.ui.sofix

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SOFIX's native VPN screen only manages the tunnel.
 * Account and finance actions open the signed Telegram Mini App instead.
 * No Telegram identity, credentials or VPN secrets travel in these URLs.
 */
@Composable
fun SofixAccountBar(openAccount: (String) -> Unit) {
    val links = listOf(
        "Подписка ↗" to "account",
        "Баланс ↗" to "wallet",
        "Продлить ↗" to "renew",
        "Автопродление ↗" to "auto",
        "Устройства ↗" to "devices",
    )
    Surface(color = Color(0xFF10231F)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            links.forEach { (title, section) ->
                OutlinedButton(
                    onClick = { openAccount(section) },
                    modifier = Modifier.height(42.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                    border = BorderStroke(1.dp, Color(0xFF477C6D)),
                ) {
                    Text(title, fontSize = 12.sp, color = Color(0xFFB6F4D8))
                }
            }
        }
    }
}

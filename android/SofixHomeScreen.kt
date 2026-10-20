package com.v2ray.ang.ui.sofix

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.v2ray.ang.BuildConfig
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val night = Color(0xFF071418)
private val panel = Color(0xFF112329)
private val outline = Color(0xFF274045)
private val mint = Color(0xFFA2F4D0)
private val muted = Color(0xFF829BA1)
private val white = Color(0xFFF2FCF9)

@Composable
fun SofixHomeScreen(
    connected: Boolean,
    configured: Boolean,
    status: String,
    onPower: () -> Unit,
    onAccount: (String) -> Unit,
    onRefresh: () -> Unit,
    onTest: () -> Unit,
    onVpnSettings: () -> Unit,
    onUpdates: () -> Unit,
    hasEntitlement: Boolean,
    isTrial: Boolean,
    trialMinutes: Int,
    linked: Boolean,
    onboardingMessage: String,
    onSubscribe: () -> Unit,
    onLink: () -> Unit,
    autoConnectOnBoot: Boolean,
    onAutoConnectOnBoot: (Boolean) -> Unit,
    onSystemVpnSettings: () -> Unit,
    networkState: String,
    subscriptionLabel: String,
    onDiagnostics: () -> Unit,
) {
    val accent by animateColorAsState(
        targetValue = if (!hasEntitlement) Color(0xFFFF666E) else if (connected) mint else Color(0xFF799DA1),
        animationSpec = tween(380), label = "vpn-accent"
    )
    val glow by animateFloatAsState(
        targetValue = if (connected || !hasEntitlement) 1f else .42f,
        animationSpec = tween(500), label = "vpn-glow"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E2529), night, Color(0xFF081316))))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 23.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(41.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFFB0FADA), Color(0xFF5BB8AA))), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("S", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF073128))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("SOFIX", color = white, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text("V P N", color = mint, fontSize = 10.sp, letterSpacing = 5.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, outline, RoundedCornerShape(14.dp))
                    .clickable { onAccount("account") }
                    .background(Color(0xFF152B2F))
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) { Text("КАБИНЕТ  ↗", color = mint, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp) }
        }
        Spacer(Modifier.height(34.dp))
        Text("ВАШЕ ПОДКЛЮЧЕНИЕ", fontSize = 10.sp, color = muted, fontWeight = FontWeight.Bold, letterSpacing = 2.6.sp)
        Spacer(Modifier.height(18.dp))
        Box(modifier = Modifier.size(274.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = .19f * glow), accent.copy(alpha = .07f * glow), Color.Transparent),
                        center = center, radius = size.minDimension / 2f
                    ),
                    radius = size.minDimension / 2f
                )
                drawCircle(color = accent.copy(alpha = .17f), radius = size.minDimension * .445f, style = Stroke(width = 1.2.dp.toPx()))
                drawCircle(color = accent.copy(alpha = .28f), radius = size.minDimension * .38f, style = Stroke(width = 1.dp.toPx()))
                drawArc(
                    color = accent,
                    startAngle = -67f,
                    sweepAngle = 314f,
                    useCenter = false,
                    topLeft = Offset(center.x - size.minDimension * .382f, center.y - size.minDimension * .382f),
                    size = Size(size.minDimension * .764f, size.minDimension * .764f),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Box(
                modifier = Modifier.size(169.dp)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFF21443E), Color(0xFF122622), Color(0xFF0D2426))),
                        CircleShape
                    )
                    .border(1.5.dp, accent.copy(alpha = .45f), CircleShape)
                    .clip(CircleShape)
                    .clickable { onPower() }
                    .semantics { contentDescription = if (connected) "Отключить VPN" else "Подключить VPN" },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(53.dp)) {
                        val w = 6.dp.toPx()
                        drawArc(
                            color = accent, startAngle = -52f, sweepAngle = 284f,
                            useCenter = false,
                            topLeft = Offset(w, w), size = Size(size.width - 2*w, size.height - 2*w),
                            style = Stroke(width = w, cap = StrokeCap.Round)
                        )
                        drawLine(
                            color = accent,
                            start = Offset(size.width/2f, size.height * .025f),
                            end = Offset(size.width/2f, size.height*.47f),
                            strokeWidth = w, cap = StrokeCap.Round
                        )
                    }
                    Spacer(Modifier.height(13.dp))
                    Text(if (!hasEntitlement) "НЕТ ПОДПИСКИ" else if (connected) "ОТКЛЮЧИТЬ" else "ПОДКЛЮЧИТЬ",
                        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp, color = white)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (connected) mint else Color(0xFF8BABB1), CircleShape))
            Spacer(Modifier.width(9.dp))
            Text(
                if (!hasEntitlement) "НЕТ ПОДПИСКИ" else if (connected && networkState=="WAITING_NETWORK") "ОЖИДАЕМ ИНТЕРНЕТ" else if (connected && networkState=="DEGRADED") "СОЕДИНЕНИЕ НЕ ПОДТВЕРЖДЕНО" else if (connected && networkState!="CONNECTED") "ПРОВЕРЯЕМ СОЕДИНЕНИЕ" else if (connected && isTrial) "ПРОБНЫЙ VPN · ${trialMinutes} МИН" else if (connected) "ЗАЩИТА АКТИВНА" else "НЕ ПОДКЛЮЧЕНО",
                fontSize = 12.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold,
                color = if (!hasEntitlement) Color(0xFFFF888E) else if (connected) mint else Color(0xFFB7CCD0)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (!hasEntitlement) "15 минут бесплатного VPN перед оформлением" else
                if (isTrial && connected) "Соединение активно. Проверяем доступность Telegram" else
                if (connected && networkState=="WAITING_NETWORK") "VPN ждёт восстановления Wi-Fi" else
                if (connected && networkState!="CONNECTED") "Проверяем доступность сети через SOFIX" else
                if (connected) "Ваш интернет защищён" else "Нажмите кнопку, чтобы включить защиту",
            color = muted, fontSize = 13.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(11.dp))
        Box(Modifier.fillMaxWidth()
            .border(1.dp,outline,RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp))
            .clickable { onAccount("account") }
            .padding(14.dp),contentAlignment=Alignment.Center) {
            Text(subscriptionLabel,color=mint,fontSize=13.sp)
        }
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier.fillMaxWidth()
                .border(1.dp, outline, RoundedCornerShape(21.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF182F34), panel)), RoundedCornerShape(21.dp))
                .clickable { onRefresh() }
                .padding(horizontal = 19.dp, vertical = 17.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(39.dp).background(Color(0xFF214239), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("◈", color = mint, fontSize = 23.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("SOFIX · Основной сервер", color = white, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(if (!hasEntitlement) "Нет подписки" else if (isTrial) "15 минут временного доступа" else "Подписка загружена",
                        color = muted, fontSize = 11.sp)
                }
                Text("↻", color = mint, fontSize = 23.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text("Нажмите, чтобы обновить подключение", color = muted, fontSize = 11.sp)
        }
        if(!hasEntitlement || isTrial){
            Spacer(Modifier.height(15.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                 .background(Brush.horizontalGradient(listOf(Color(0xFFB4F7D9),Color(0xFF6FD4BC))))
                 .clickable { onSubscribe() }
                 .padding(19.dp),contentAlignment = Alignment.Center){
                 Text("ОФОРМИТЬ ПОДПИСКУ  →", color=Color(0xFF0B3329),fontSize=14.sp,fontWeight=FontWeight.ExtraBold)
            }
        }
        if(onboardingMessage.isNotBlank()){
            Spacer(Modifier.height(12.dp))
            Text(onboardingMessage, color=muted,fontSize=12.sp,textAlign=TextAlign.Center)
            if(isTrial && !linked && connected){
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .border(1.dp,outline,RoundedCornerShape(12.dp)).clickable { onLink() }
                    .padding(13.dp),contentAlignment=Alignment.Center){
                    Text("Проверить доступность Telegram ↗",color=mint,fontSize=12.sp)
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            NavTile("ПОДПИСКА", "↗", Modifier.weight(1f)) { onAccount("account") }
            NavTile("УСТРОЙСТВА", "▦", Modifier.weight(1f)) { onAccount("devices") }
        }
        Spacer(Modifier.height(11.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            NavTile("ПРОДЛИТЬ", "+", Modifier.weight(1f)) { onAccount("renew") }
            NavTile("НАСТРОЙКИ", "⚙", Modifier.weight(1f)) { onVpnSettings() }
        }
        Spacer(Modifier.height(12.dp))
        NavTile("ПРОВЕРИТЬ ОБНОВЛЕНИЯ", "↓", Modifier.fillMaxWidth()) { onUpdates() }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("ПРОВЕРИТЬ СОЕДИНЕНИЕ", modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onDiagnostics() }
                .padding(8.dp),
                color = if (connected) mint else muted, fontWeight = FontWeight.Bold,
                fontSize = 10.sp, letterSpacing = 1.3.sp)
            Text("  •  ", color = outline)
            Text(BuildConfig.VERSION_NAME.uppercase(), color = muted, fontSize = 10.sp)
        }
        if (connected && status.isNotBlank()) {
            Text(status, color = muted, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun NavTile(label: String, symbol: String, modifier: Modifier, action: () -> Unit) {
    Row(
        modifier = modifier
            .border(1.dp, outline, RoundedCornerShape(16.dp))
            .background(Color(0xFF11262A), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = action)
            .padding(horizontal = 15.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = white, fontWeight = FontWeight.SemiBold, letterSpacing = .7.sp, fontSize = 10.sp)
        Text(symbol, color = mint, fontSize = 19.sp)
    }
}

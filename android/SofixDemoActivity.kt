package com.v2ray.ang.ui.sofix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Entirely local preview. No token, API, activity result, device mutation,
 * subscription provisioning or real VPN start is permitted here.
 */
class SofixDemoActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var step by remember {mutableIntStateOf(0)}
            val titles=listOf("Нет подписки","15 минут бесплатно","VPN подключается",
                "Интернет работает","Telegram Mini App","Телефон привязан")
            val hints=listOf(
                "Так встречаем нового пользователя. Регистрация пока не нужна.",
                "Сначала предложим пробный доступ. В демо реальный ключ не выдаётся.",
                "Здесь Android попросит разрешить VPN. В демо подключение не запускается.",
                "В настоящем сценарии SOFIX проверяет интернет и доступность Telegram.",
                "Mini App создаёт аккаунт и подтверждает телефон без шестизначного кода.",
                "В демо ничего не изменилось. Реальные подписка и устройство сохранены."
            )
            Column(Modifier.fillMaxSize().background(Color(0xFF08171B)).padding(24.dp),
                horizontalAlignment=Alignment.CenterHorizontally){
                Text("‹ Выйти из демонстрации",color=Color(0xFFA2F4D0),
                    modifier=Modifier.align(Alignment.Start).clickable{finish()})
                Spacer(Modifier.height(20.dp))
                Text("ДЕМОНСТРАЦИЯ — реальный VPN не меняется",
                    color=Color(0xFFFFC476),fontSize=12.sp,textAlign=TextAlign.Center)
                Spacer(Modifier.weight(1f))
                Text("SOFIX / VPN",fontSize=20.sp,color=Color.White,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(25.dp))
                Box(Modifier.size(210.dp).background(
                    if(step==0)Color(0xFF5B252B) else Color(0xFF163E37),
                    RoundedCornerShape(110.dp)),contentAlignment=Alignment.Center){
                    Text(if(step==0)"⏻" else "✓",fontSize=64.sp,
                        color=if(step==0)Color(0xFFFF7979) else Color(0xFFA2F4D0))
                }
                Spacer(Modifier.height(23.dp))
                Text(titles[step],color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(hints[step],color=Color(0xFFAAC5C4),textAlign=TextAlign.Center,fontSize=14.sp)
                Spacer(Modifier.height(25.dp))
                Button(onClick={step=(step+1)%titles.size},modifier=Modifier.fillMaxWidth()) {
                    Text(if(step==titles.lastIndex)"Начать заново" else "Следующий шаг →")
                }
                Spacer(Modifier.weight(1f))
                Text("Экран "+(step+1)+" из "+titles.size+" · Только макет",
                    color=Color(0xFF87A3A3),fontSize=11.sp)
            }
        }
    }
}

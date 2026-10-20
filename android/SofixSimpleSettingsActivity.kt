package com.v2ray.ang.ui.sofix

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.settings.SettingsActivity

/** SOFIX settings for ordinary people. Legacy v2rayNG preferences stay opt-in. */
class SofixSimpleSettingsActivity: ComponentActivity() {
    private var autostart by mutableStateOf(false)
    private var advanced by mutableStateOf(false)
    private var explanation by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(9,22,27)
        window.navigationBarColor=android.graphics.Color.rgb(9,22,27)
        val prefs=getSharedPreferences("sofix_ui",MODE_PRIVATE)
        advanced=prefs.getBoolean("advanced",false)
        autostart=MmkvManager.decodeStartOnBoot()
        setContent {
            SimpleSettingsScreen(
                boot=autostart,
                advanced=advanced,
                hint=explanation,
                onBack={finish()},
                onBoot={
                    MmkvManager.encodeStartOnBoot(it)
                    autostart=it
                },
                onAdvanced={
                    advanced=it
                    prefs.edit().putBoolean("advanced",it).apply()
                },
                onAndroidVpn={
                    try {startActivity(Intent(Settings.ACTION_VPN_SETTINGS))}
                    catch(e:Exception) {explanation="Откройте Настройки Android → VPN → SOFIX VPN."}
                },
                onUpdate={startActivity(Intent(this,SofixUpdateActivity::class.java))},
                onExpert={startActivity(Intent(this,SettingsActivity::class.java))}
            )
        }
    }

    override fun onResume() {
        super.onResume()
        autostart=MmkvManager.decodeStartOnBoot()
    }
}

private val ink=Color(0xFF08171B)
private val surf=Color(0xFF14292D)
private val edge=Color(0xFF2A4849)
private val m=Color(0xFF9FF2CC)
private val txt=Color(0xFFEAF9F4)
private val soft=Color(0xFF93ABAB)

@Composable
private fun SettingRow(
    title:String,
    description:String,
    modifier:Modifier=Modifier,
    checked:Boolean?=null,
    onToggle:((Boolean)->Unit)?=null,
    onClick:(()->Unit)?=null
) {
    Row(
        modifier=modifier.fillMaxWidth()
            .border(1.dp,edge,RoundedCornerShape(17.dp))
            .background(surf,RoundedCornerShape(17.dp))
            .clickable(enabled=onClick!=null || onToggle!=null) {
                if(checked!=null)onToggle?.invoke(!checked) else onClick?.invoke()
            }
            .padding(horizontal=16.dp,vertical=16.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title,color=txt,fontWeight=FontWeight.SemiBold,fontSize=15.sp)
            Spacer(Modifier.height(5.dp))
            Text(description,color=soft,fontSize=12.sp,lineHeight=18.sp)
        }
        if(checked!=null && onToggle!=null) {
            Switch(checked=checked,onCheckedChange=onToggle,
                colors=SwitchDefaults.colors(checkedTrackColor=m,checkedThumbColor=ink))
        } else {
            Text("›",color=m,fontSize=27.sp)
        }
    }
}

@Composable
private fun SimpleSettingsScreen(
    boot:Boolean,
    advanced:Boolean,
    hint:String,
    onBack:()->Unit,
    onBoot:(Boolean)->Unit,
    onAdvanced:(Boolean)->Unit,
    onAndroidVpn:()->Unit,
    onUpdate:()->Unit,
    onExpert:()->Unit
) {
    Column(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFF123036),ink,ink)))
        .verticalScroll(rememberScrollState())
        .padding(horizontal=22.dp,vertical=30.dp)
    ) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("‹  К VPN",color=m,fontWeight=FontWeight.SemiBold,fontSize=13.sp,
                modifier=Modifier.clickable(onClick=onBack).padding(vertical=10.dp))
            Spacer(Modifier.weight(1f))
            Text("SOFIX",color=txt,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text("Настройки",color=txt,fontSize=30.sp,fontWeight=FontWeight.ExtraBold)
        Spacer(Modifier.height(7.dp))
        Text("Самое необходимое для стабильной работы VPN.",color=soft,fontSize=14.sp)
        Spacer(Modifier.height(26.dp))
        Text("ПОДКЛЮЧЕНИЕ",color=m,fontSize=11.sp,
            fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
        Spacer(Modifier.height(11.dp))
        SettingRow("Включать VPN после перезагрузки",
            "SOFIX автоматически восстановит соединение после запуска телефона.",
            checked=boot,onToggle=onBoot)
        Spacer(Modifier.height(11.dp))
        SettingRow("Постоянная VPN",
            "Дополнительная защита Android: телефон может сам поддерживать VPN-подключение.",
            onClick=onAndroidVpn)
        if(hint.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(hint,color=m,fontSize=12.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text("ПРИЛОЖЕНИЕ",color=m,fontSize=11.sp,
            fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
        Spacer(Modifier.height(11.dp))
        SettingRow("Обновить SOFIX VPN",
            "Узнать о новой версии, посмотреть изменения и установить обновление.",
            onClick=onUpdate)
        Spacer(Modifier.height(24.dp))
        Text("ДЛЯ ОПЫТНЫХ",color=m,fontSize=11.sp,
            fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
        Spacer(Modifier.height(11.dp))
        SettingRow("Расширенные настройки",
            "Маршрутизация, DNS, прокси, режим туннеля и другие технические параметры.",
            checked=advanced,onToggle=onAdvanced)
        if(advanced) {
            Spacer(Modifier.height(11.dp))
            SettingRow("Открыть технические настройки",
                "Полный набор параметров VPN. Меняйте их только если понимаете назначение.",
                onClick=onExpert)
        }
        Spacer(Modifier.height(23.dp))
        Text("SOFIX VPN · "+BuildConfig.VERSION_NAME,
            color=soft,fontSize=11.sp)
        Spacer(Modifier.height(32.dp))
    }
}

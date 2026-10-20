package com.v2ray.ang.ui.sofix

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URL

/** VPN icon != usable internet. The proxy route is verified against direct egress. */
object SofixHealth {
    data class State(val name:String,val detail:String, val dns:String="—",
        val proxy:String="—",val direct:String="—",val telegram:String="—")
    private fun get(url:String, connection:java.net.URLConnection?=null):Pair<Boolean,String>{
        val c=(connection?:URL(url).openConnection()) as HttpURLConnection
        try {
            c.connectTimeout=5000;c.readTimeout=5000
            c.instanceFollowRedirects=false
            val code=c.responseCode
            if(code !in 200..399)return false to "HTTP $code"
            if(url.contains("api.ipify.org")) {
                val ip=c.inputStream.bufferedReader().use{it.readText().trim()}
                return (ip.matches(Regex("[a-fA-F0-9.:]{7,45}"))) to ip
            }
            return true to "Ответ HTTP $code"
        }catch(e:Exception){return false to (e.javaClass.simpleName)}
        finally {c.disconnect()}
    }
    fun physicalAvailable(c:Context):Boolean {
        val cm=c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.allNetworks.any{ network ->
            val caps=cm.getNetworkCapabilities(network)
            caps!=null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                 caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                 caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
    fun fast(c:Context,running:Boolean):String =
        if(!running)"OFF" else if(!physicalAvailable(c))"WAITING_NETWORK" else "CHECKING"
    fun sample(c:Context,running:Boolean):State {
        if(!running)return State("OFF","VPN выключен")
        if(!physicalAvailable(c))return State("WAITING_NETWORK","Ожидаем Wi-Fi или мобильную сеть")
        val cm=c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap=cm.getNetworkCapabilities(cm.activeNetwork)
        val vpn=cm.allNetworks.any{n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true
        }
        if(!vpn)return State("CONNECTING","Android ещё не поднял VPN")
        val proxyIp=get("https://api.ipify.org")
        val physicalNetwork=cm.allNetworks.firstOrNull{n ->
            val props=cm.getNetworkCapabilities(n)
            props!=null && !props.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                props.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        val directIp=if(physicalNetwork!=null) {
            try{get("https://api.ipify.org",physicalNetwork.openConnection(URL("https://api.ipify.org")))}
            catch(e:Exception){false to e.javaClass.simpleName}
        }else false to "Физическая сеть не найдена"
        val tg=get("https://telegram.org")
        val established=proxyIp.first && directIp.first && proxyIp.second!=directIp.second
        val status=if(established)"CONNECTED" else "DEGRADED"
        val message=if(established)"VPN работает, внешний адрес отличается от прямого"
            else "VPN запущен, но маршрут до сервера не подтверждён"
        return State(status,message,if(proxyIp.first)"Работает" else proxyIp.second,
            if(proxyIp.first)proxyIp.second else "Нет ответа",
            if(directIp.first)directIp.second else "Нет ответа",
            if(tg.first)"Доступен" else "Не отвечает")
    }
}

class SofixDiagnosticsActivity:ComponentActivity(){
    private var state by mutableStateOf(SofixHealth.State("CHECKING","Проверяем соединение…"))
    private var busy by mutableStateOf(false)
    private fun scan(){
        if(busy)return
        busy=true
        Thread {
            val result=SofixHealth.sample(this,true)
            runOnUiThread{state=result;busy=false}
        }.start()
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);scan()
        setContent {
            Column(Modifier.fillMaxSize().background(Color(0xFF08171B))
                .verticalScroll(rememberScrollState()).padding(24.dp)){
                Text("‹ НАЗАД",color=Color(0xFFA2F4D0),modifier=Modifier.clickable{finish()})
                Spacer(Modifier.height(24.dp))
                Text("Проверка подключения",color=Color.White,fontSize=25.sp,
                    fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Text(state.detail,color=Color(0xFFA2F4D0),fontSize=14.sp)
                Spacer(Modifier.height(18.dp))
                listOf("VPN и маршрут" to state.proxy,
                    "Прямой интернет" to state.direct,
                    "DNS и доступность" to state.dns,
                    "Telegram" to state.telegram).forEach { (name,result) ->
                    Column(Modifier.fillMaxWidth().padding(bottom=10.dp)
                        .background(Color(0xFF152C30),RoundedCornerShape(14.dp))
                        .padding(15.dp)){
                        Text(name,color=Color.White,fontWeight=FontWeight.Bold)
                        Text(result,color=Color(0xFFB5C9C8),fontSize=12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick={scan()},enabled=!busy,modifier=Modifier.fillMaxWidth()){
                    Text(if(busy)"Проверяем…" else "Повторить проверку")
                }
                Spacer(Modifier.height(8.dp))
                Text("Контрольный VPN-запрос сравнивается с выходом напрямую. "+
                    "Если один путь недоступен, защита не считается подтверждённой. "+
                    "Сайты, которые вы посещаете, не собираются.",
                    color=Color(0xFF9AB5B5),fontSize=12.sp)
            }
        }
    }
}

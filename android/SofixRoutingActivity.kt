package com.v2ray.ang.ui.sofix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

private val bg=Color(0xFF08171B)
private val card=Color(0xFF142A2F)
private val mint=Color(0xFFA2F4D0)
private val white=Color(0xFFF2FCF9)
private val muted=Color(0xFF9AB5B5)

class SofixRoutingActivity:ComponentActivity() {
    private var activeMode by mutableStateOf("smart")
    private var proxyItems by mutableStateOf<List<String>>(emptyList())
    private var directItems by mutableStateOf<List<String>>(emptyList())
    private var input by mutableStateOf("")
    private var lookup by mutableStateOf("")
    private var result by mutableStateOf("")
    private var notice by mutableStateOf("")
    private var addingProxy by mutableStateOf<Boolean?>(null)
    private var version by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        refreshLocal()
        setContent {
            Column(Modifier.fillMaxSize().background(bg)
                .verticalScroll(rememberScrollState()).padding(22.dp)) {
                Text("‹  НАЗАД",color=mint,fontSize=13.sp,
                    modifier=Modifier.clickable{finish()}.padding(vertical=9.dp))
                Spacer(Modifier.height(20.dp))
                Text("Раздельное туннелирование",color=white,
                    fontSize=25.sp,fontWeight=FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text("SOFIX выбирает путь к сайтам. Вы всегда можете изменить решение.",
                    color=muted,fontSize=13.sp)
                Spacer(Modifier.height(22.dp))
                listOf(
                    Triple("smart","Умный · рекомендуется",
                        "Российские сайты напрямую, ограниченные и неизвестные — через VPN"),
                    Triple("all","Весь интернет через VPN",
                        "Публичные подключения через SOFIX, кроме явных исключений"),
                    Triple("selected","Только выбранные сайты",
                        "Напрямую всё, кроме списка «Всегда через VPN»")
                ).forEach { (id,title,desc) ->
                    Row(Modifier.fillMaxWidth().padding(bottom=9.dp)
                        .background(card,RoundedCornerShape(15.dp))
                        .border(1.dp,if(activeMode==id)mint else Color(0xFF284A4A),RoundedCornerShape(15.dp))
                        .clickable{
                            SofixRouting.setMode(this@SofixRoutingActivity,id)
                            refreshLocal()
                            notice="Режим сохранён. Переподключите VPN для применения."
                        }.padding(15.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title,color=white,fontWeight=FontWeight.Bold,fontSize=14.sp)
                            Spacer(Modifier.height(5.dp))
                            Text(desc,color=muted,fontSize=12.sp)
                        }
                        RadioButton(selected=activeMode==id,onClick={
                            SofixRouting.setMode(this@SofixRoutingActivity,id)
                            refreshLocal()
                            notice="Режим сохранён. Переподключите VPN для применения."
                        })
                    }
                }
                Spacer(Modifier.height(17.dp))
                ListGroup("Всегда через VPN",proxyItems,true)
                Spacer(Modifier.height(13.dp))
                ListGroup("Всегда напрямую",directItems,false)
                Spacer(Modifier.height(20.dp))
                Text("Как откроется сайт?",color=white,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value=lookup,onValueChange={lookup=it},singleLine=true,
                    label={Text("Адрес сайта")},modifier=Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Action("Показать маршрут") {
                    try {
                        val evaluation=SofixRouting.explain(this@SofixRoutingActivity,lookup)
                        result=(if(evaluation.first=="proxy")"Через VPN" else "Напрямую")+
                            " · "+evaluation.second
                    }catch(e:Exception){result=e.message?:"Неправильный адрес"}
                }
                if(result.isNotBlank())Text(result,color=mint,modifier=Modifier.padding(top=8.dp))
                Spacer(Modifier.height(22.dp))
                Text("Списки SOFIX · версия $version",color=white,fontWeight=FontWeight.SemiBold)
                Text("Обновляются отдельно от APK, с цифровой подписью.",
                    color=muted,fontSize=12.sp)
                Spacer(Modifier.height(7.dp))
                Action("Проверить обновления правил") {
                    notice="Проверяем цифровую подпись…"
                    Thread{
                        val outcome=try{SofixRuleUpdates.refresh(this@SofixRoutingActivity,true)}
                        catch(e:Exception){"Не удалось обновить: "+(e.message?:"ошибка проверки")}
                        runOnUiThread { refreshLocal();notice=outcome+"; переподключите VPN для применения."}
                    }.start()
                }
                if(notice.isNotBlank()) Text(notice,color=mint,
                    fontSize=12.sp,modifier=Modifier.padding(top=10.dp))
                Spacer(Modifier.height(35.dp))
            }
            if(addingProxy!=null)AlertDialog(
                onDismissRequest={addingProxy=null;input=""},
                title={Text(if(addingProxy==true)"Всегда через VPN" else "Всегда напрямую")},
                text={
                    Column {
                        Text("Адрес будет применён к сайту и его поддоменам.")
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value=input,onValueChange={input=it},
                            label={Text("Например, example.ru")},singleLine=true)
                    }
                },
                confirmButton={TextButton(onClick={
                    try {
                        SofixRouting.mutate(this@SofixRoutingActivity,input,addingProxy==true)
                        notice="Правило сохранено. Переподключите VPN для применения."
                        refreshLocal();addingProxy=null;input=""
                    }catch(e:Exception){notice=e.message?:"Неверный домен";addingProxy=null}
                }){Text("Сохранить")}},
                dismissButton={TextButton(onClick={addingProxy=null}){Text("Отмена")}}
            )
        }
    }
    private fun refreshLocal(){
        activeMode=SofixRouting.mode(this)
        proxyItems=SofixRouting.entries(this,true)
        directItems=SofixRouting.entries(this,false)
        version=SofixRuleUpdates.version(this)
    }
    @Composable private fun Action(label:String,onClick:()->Unit) {
        Box(Modifier.fillMaxWidth().background(mint,RoundedCornerShape(12.dp))
            .clickable(onClick=onClick).padding(13.dp),contentAlignment=Alignment.Center){
            Text(label,color=bg,fontWeight=FontWeight.Bold,fontSize=13.sp)
        }
    }
    @Composable private fun ListGroup(title:String,items:List<String>,proxy:Boolean){
        Column(Modifier.fillMaxWidth().background(card,RoundedCornerShape(16.dp))
            .padding(15.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(title+" · "+items.size,color=white,fontWeight=FontWeight.Bold,
                    modifier=Modifier.weight(1f))
                Text("＋ Добавить",color=mint,fontSize=12.sp,
                    modifier=Modifier.clickable{addingProxy=proxy;input=""})
            }
            if(items.isEmpty()) {
                Spacer(Modifier.height(7.dp))
                Text("Ваш список пока пуст",color=muted,fontSize=12.sp)
            }
            items.forEach { host ->
                Row(Modifier.fillMaxWidth().padding(top=10.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Text(host,color=white,fontSize=13.sp,modifier=Modifier.weight(1f))
                    Text("Убрать ×",color=mint,fontSize=12.sp,
                        modifier=Modifier.clickable{
                            SofixRouting.mutate(this@SofixRoutingActivity,host,proxy,remove=true)
                            refreshLocal()
                            notice="Правило удалено. Переподключите VPN."
                        })
                }
            }
        }
    }
}

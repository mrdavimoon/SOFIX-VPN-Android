package com.v2ray.ang.ui.sofix

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val night=Color(0xFF08161A)
private val panel=Color(0xFF14292E)
private val mint=Color(0xFFA4F4D2)
private val white=Color(0xFFEFFCF7)
private val muted=Color(0xFF9AB2B2)

@Composable
fun SofixUpdateScreen(
    current:String,
    latest:String?,
    message:String,
    changes:String,
    stage:String,
    progress:Int,
    busy:Boolean,
    updateAvailable:Boolean,
    onCheck:()->Unit,
    onDownload:()->Unit,
    onBack:()->Unit,
) {
    val fraction by animateFloatAsState(
        targetValue=(progress.coerceIn(0,100)/100f),
        animationSpec=tween(420), label="download-progress"
    )
    val tint by animateColorAsState(
        targetValue=if(stage=="error")Color(0xFFFF7D83) else mint,
        animationSpec=tween(250),label="update-color"
    )
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF102D30),night,night)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal=22.dp, vertical=30.dp),
        horizontalAlignment=Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("‹  НАЗАД", color=mint, fontSize=12.sp, letterSpacing=1.sp,
                 modifier=Modifier.clickable(onClick=onBack).padding(vertical=12.dp))
            Spacer(Modifier.weight(1f))
            Text("SOFIX / VPN",color=white,fontSize=13.sp,
                 fontWeight=FontWeight.Bold,letterSpacing=2.sp)
        }
        Spacer(Modifier.height(26.dp))
        Text("Обновления", color=white,fontWeight=FontWeight.ExtraBold,fontSize=29.sp)
        Spacer(Modifier.height(5.dp))
        Text("Текущая версия  $current",color=muted,fontSize=13.sp)
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier.fillMaxWidth().border(1.dp,Color(0xFF33564F),RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF193A35),panel)),RoundedCornerShape(24.dp))
                .padding(22.dp),
            contentAlignment=Alignment.Center
        ) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Box(Modifier.size(185.dp),contentAlignment=Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val width=8.dp.toPx()
                        drawArc(color=Color(0xFF3A5B58), startAngle=-90f,sweepAngle=360f,
                            useCenter=false, style=androidx.compose.ui.graphics.drawscope.Stroke(width=width,cap=StrokeCap.Round))
                        drawArc(color=tint,startAngle=-90f,
                            sweepAngle=if(stage=="checking")115f else if(stage=="downloading")fraction*360f else 360f,
                            useCenter=false,
                            style=androidx.compose.ui.graphics.drawscope.Stroke(width=width,cap=StrokeCap.Round))
                    }
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(
                            when(stage) {
                                "checking"->"↻"
                                "downloading"->"$progress%"
                                "verified"->"✓"
                                "error"->"!"
                                else->if(updateAvailable)"↓" else "✓"
                            },
                            fontSize=if(stage=="downloading")37.sp else 56.sp,
                            color=tint,fontWeight=FontWeight.Bold
                        )
                        Text(when(stage) {
                            "checking"->"ПРОВЕРЯЕМ"
                            "downloading"->"ЗАГРУЗКА"
                            "verified"->"ПРОВЕРЕНО"
                            "error"->"ОШИБКА"
                            else->if(updateAvailable)"НОВАЯ ВЕРСИЯ" else "ВСЁ АКТУАЛЬНО"
                        },color=white,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
                    }
                }
                Spacer(Modifier.height(22.dp))
                if (latest != null) {
                    Text("$current   →   $latest",color=mint,
                        fontWeight=FontWeight.Bold,fontSize=17.sp,textAlign=TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                }
                Text(message,color=white,fontSize=14.sp,textAlign=TextAlign.Center,lineHeight=20.sp)
                Spacer(Modifier.height(20.dp))
                if(stage=="downloading") {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                        Text("Загружаем APK",color=muted,fontSize=12.sp)
                        Text("$progress%",color=mint,fontSize=12.sp,fontWeight=FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress={fraction},modifier=Modifier.fillMaxWidth().height(6.dp),
                        color=mint,trackColor=Color(0xFF355651),strokeCap=StrokeCap.Round
                    )
                    Spacer(Modifier.height(17.dp))
                }
                Box(
                    Modifier.fillMaxWidth()
                        .background(
                            if(busy) Color(0xFF41645C) else mint,
                            RoundedCornerShape(16.dp))
                        .clickable(enabled=!busy) { if(updateAvailable)onDownload() else onCheck() }
                        .padding(horizontal=18.dp,vertical=17.dp),
                    contentAlignment=Alignment.Center
                ) {
                    Text(when {
                        stage=="downloading"->"СКАЧИВАЕМ…"
                        stage=="checking"->"ПРОВЕРЯЕМ…"
                        stage=="verified"->"УСТАНОВКА ЧЕРЕЗ ANDROID"
                        updateAvailable->"СКАЧАТЬ И ОБНОВИТЬ  ↓"
                        else->"ПРОВЕРИТЬ ЕЩЁ РАЗ  ↻"
                    },color=Color(0xFF0B3128),fontSize=13.sp,
                        fontWeight=FontWeight.ExtraBold,letterSpacing=.7.sp)
                }
            }
        }
        if(changes.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth()
                .border(1.dp,Color(0xFF254447),RoundedCornerShape(20.dp))
                .background(Color(0xFF12272B),RoundedCornerShape(20.dp))
                .padding(19.dp)) {
                Text("ЧТО НОВОГО",color=mint,fontSize=11.sp,
                    fontWeight=FontWeight.Bold,letterSpacing=1.5.sp)
                Spacer(Modifier.height(12.dp))
                Text(changes.lineSequence().map { it.trimStart('#','*',' ','-') }
                        .filter { it.isNotBlank() }.take(24).joinToString("\n"),
                    color=muted,fontSize=13.sp,lineHeight=21.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("APK проверяется перед установкой. Android попросит подтвердить обновление. Настройки и подписка сохраняются.",
            color=muted,fontSize=11.sp,textAlign=TextAlign.Center,lineHeight=16.sp)
        Spacer(Modifier.height(30.dp))
    }
}

package com.v2ray.ang.ui.sofix

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import com.v2ray.ang.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** In-app release check, verified APK download, and user-approved Android installation. */
class SofixUpdateActivity : Activity() {
    private data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val shaUrl: String,
        val apkBytes: Long,
    )

    private val busy = AtomicBoolean(false)
    private lateinit var message: TextView
    private lateinit var notes: TextView
    private lateinit var check: Button
    private lateinit var install: Button
    private var candidate: Release? = null
    private var verifiedFile: File? = null
    private val pkgApi = "https://api.github.com/repos/mrdavimoon/SOFIX-VPN-Android/releases?per_page=10"
    private val versionPattern = Regex("^v(\\d+)\\.(\\d+)\\.(\\d+)(?:-beta\\.(\\d+))?$")
    private val releaseHost = "https://github.com/mrdavimoon/SOFIX-VPN-Android/releases/download/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 21, 25)
        window.navigationBarColor = Color.rgb(7, 21, 25)
        val screen = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(7, 21, 25))
        }
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24*dp).toInt(),(45*dp).toInt(),(24*dp).toInt(),(30*dp).toInt())
        }
        fun label(s: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
            text=s; textSize=size; setTextColor(color)
            setPadding(0,(9*dp).toInt(),0,(9*dp).toInt())
        }
        fun rounded(bg: Int) = GradientDrawable().apply {
            setColor(bg); cornerRadius = 15*dp
        }
        fun button(s:String) = Button(this).apply {
            text = s; isAllCaps = false
            background = rounded(Color.rgb(158, 241, 204))
            setTextColor(Color.rgb(9,45,37)); setTypeface(null,Typeface.BOLD)
        }
        root.addView(label("SOFIX  /  VPN",30f,Color.rgb(158,241,204)).apply {
            setTypeface(null,Typeface.BOLD)
        })
        root.addView(label("Обновления приложения",24f).apply { setTypeface(null,Typeface.BOLD) })
        root.addView(label("Установлено: "+BuildConfig.VERSION_NAME,14f,Color.rgb(164,191,188)))
        message=label("Нажмите «Проверить обновления».",14f,Color.rgb(190,213,206))
        root.addView(message)
        check=button("Проверить обновления").apply { setOnClickListener { checkReleases() } }
        root.addView(check)
        notes=label("",13f,Color.rgb(180,203,197))
        root.addView(notes)
        install=button("Скачать и установить").apply {
            visibility = View.GONE
            setOnClickListener { downloadUpdate() }
        }
        root.addView(install)
        root.addView(label("Android попросит подтвердить установку. Подписка и настройки сохранятся при обновлении поверх приложения с той же подписью.",12f,Color.rgb(129,156,158)))
        val back=label("← Вернуться к VPN",14f,Color.rgb(158,241,204))
        back.gravity=Gravity.CENTER
        back.setOnClickListener { finish() }
        root.addView(back)
        screen.addView(root)
        setContentView(screen)
        checkReleases()
    }

    override fun onResume() {
        super.onResume()
        verifiedFile?.let {
            if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) {
                verifiedFile = null
                showInstaller(it)
            }
        }
    }

    private fun ui(callback: () -> Unit) = runOnUiThread {
        if (!isFinishing && !isDestroyed) callback()
    }

    private fun async(task: () -> Unit) {
        if (!busy.compareAndSet(false,true)) return
        check.isEnabled = false
        install.isEnabled = false
        Thread {
            try { task() }
            finally {
                busy.set(false)
                ui { check.isEnabled=true; install.isEnabled=true }
            }
        }.start()
    }

    private fun fetch(url: String, maxBytes: Int): ByteArray {
        if (!url.startsWith("https://")) throw IllegalArgumentException("Небезопасный адрес обновления")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=10000
            connection.readTimeout=16000
            connection.setRequestProperty("Accept","application/vnd.github+json")
            connection.setRequestProperty("User-Agent","SOFIX-VPN-Android/"+BuildConfig.VERSION_NAME)
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Сервер обновлений ответил: "+connection.responseCode)
            }
            connection.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream()
                val chunk=ByteArray(8192)
                while (true) {
                    val read=input.read(chunk)
                    if (read < 0) break
                    if (output.size()+read > maxBytes) throw IllegalStateException("Ответ сервера слишком большой")
                    output.write(chunk,0,read)
                }
                return output.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    private fun parsedVersion(s: String): List<Int>? {
        val match=versionPattern.matchEntire(if (s.startsWith("v")) s else "v"+s) ?: return null
        return (1..3).map { match.groupValues[it].toIntOrNull() ?: return null } +
            listOf(if (match.groupValues[4].isEmpty()) Int.MAX_VALUE
                   else match.groupValues[4].toIntOrNull() ?: return null)
    }

    private fun newer(a: String,b: String): Boolean {
        val candidate=parsedVersion(a) ?: return false
        val installed=parsedVersion(b) ?: return false
        return (0..3).firstOrNull { candidate[it]!=installed[it] }?.let { candidate[it]>installed[it] } ?: false
    }

    private fun releaseFrom(json: JSONObject): Release? {
        if (json.optBoolean("draft",false)) return null
        val tag=json.optString("tag_name")
        if (!newer(tag,BuildConfig.VERSION_NAME)) return null
        val assets=json.optJSONArray("assets") ?: return null
        val apk=findAsset(assets,"SOFIX-VPN-Android-arm64-beta.apk") ?: return null
        val sha=findAsset(assets,"SHA256SUMS") ?: return null
        if (!apk.first.startsWith(releaseHost) || !sha.first.startsWith(releaseHost)) return null
        val size=apk.second
        if (size < 3_000_000 || size > 120_000_000) return null
        return Release(tag, json.optString("body").take(6500),apk.first,sha.first,size)
    }

    private fun findAsset(assets:JSONArray,name:String): Pair<String,Long>? {
        for (i in 0 until assets.length()) {
            val a=assets.optJSONObject(i) ?: continue
            if (a.optString("name")==name) return Pair(a.optString("browser_download_url"),a.optLong("size"))
        }
        return null
    }

    private fun checkReleases() {
        message.text="Проверяем обновления…"
        notes.text=""
        install.visibility=View.GONE
        candidate=null
        async {
            try {
                val releases=JSONArray(String(fetch(pkgApi,1_500_000),Charsets.UTF_8))
                var winner:Release?=null
                for (i in 0 until releases.length()) {
                    val item=releases.optJSONObject(i) ?: continue
                    val next=releaseFrom(item) ?: continue
                    if (winner==null || newer(next.version,winner.version)) winner=next
                }
                val nextRelease=winner
                candidate=nextRelease
                ui {
                    if (nextRelease==null) message.text="У вас последняя доступная версия SOFIX VPN."
                    else {
                        message.text="Доступно обновление "+nextRelease.version+" · "+
                            (nextRelease.apkBytes/1_000_000)+" МБ"
                        notes.text=nextRelease.notes.ifBlank { "Список изменений пока не указан." }
                        install.visibility=View.VISIBLE
                    }
                }
            } catch(e: Exception) {ui { message.text="Не удалось проверить обновления: "+e.message }}
        }
    }

    private fun downloadUpdate() {
        val release=candidate ?: return
        message.text="Проверяем подпись файла и готовим загрузку…"
        async {
            val out=File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir,
                         "sofix-update.apk")
            try {
                val sha=String(fetch(release.shaUrl,4096),Charsets.UTF_8)
                val checksum=Regex("^([A-Fa-f0-9]{64})\\s+SOFIX-VPN-Android-arm64-beta\\.apk$",RegexOption.MULTILINE)
                    .find(sha)?.groupValues?.get(1)?.lowercase()
                    ?: throw IllegalStateException("Нет контрольной суммы обновления")
                val connection=URL(release.apkUrl).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout=12000
                    connection.readTimeout=30000
                    connection.setRequestProperty("User-Agent","SOFIX-VPN-Android/"+BuildConfig.VERSION_NAME)
                    if (connection.responseCode !in 200..299) throw IllegalStateException("Не удалось скачать APK")
                    val md=MessageDigest.getInstance("SHA-256")
                    var count=0L
                    var lastProgress=-1
                    connection.inputStream.use { input ->
                        out.outputStream().use { output ->
                            val bytes=ByteArray(32768)
                            while (true) {
                                val n=input.read(bytes)
                                if (n<0) break
                                count+=n
                                if (count>120_000_000) throw IllegalStateException("APK превышает допустимый размер")
                                output.write(bytes,0,n)
                                md.update(bytes,0,n)
                                val progress=(count*100/release.apkBytes).toInt().coerceAtMost(100)
                                if (progress/5>lastProgress/5) {
                                    lastProgress=progress
                                    ui { message.text="Загружаем обновление: $progress%" }
                                }
                            }
                        }
                    }
                    if (count!=release.apkBytes) throw IllegalStateException("APK загружен не полностью")
                    val actual=md.digest().joinToString("") { "%02x".format(it) }
                    if (!actual.equals(checksum,true)) throw IllegalStateException("Контрольная сумма APK не совпала")
                    val apkInfo=packageManager.getPackageArchiveInfo(out.absolutePath,0)
                        ?: throw IllegalStateException("Загруженный файл не является APK")
                    if (apkInfo.packageName!=packageName) throw IllegalStateException("APK предназначен для другого приложения")
                    @Suppress("DEPRECATION")
                    if (apkInfo.versionCode<=packageManager.getPackageInfo(packageName,0).versionCode)
                        throw IllegalStateException("Загруженная версия не новее установленной")
                    ui {
                        message.text="Файл проверен. Для обновления Android запросит подтверждение."
                        verifiedFile=out
                        beginInstall(out)
                    }
                } finally { connection.disconnect() }
            } catch(e:Exception) {
                out.delete()
                ui { message.text="Ошибка обновления: "+e.message }
            }
        }
    }

    private fun beginInstall(apk:File) {
        if (Build.VERSION.SDK_INT>=26 && !packageManager.canRequestPackageInstalls()) {
            message.text="Разрешите установку обновлений из SOFIX VPN в настройках Android."
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:"+packageName)))
            } catch (_:ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            }
            return
        }
        verifiedFile=null
        showInstaller(apk)
    }

    private fun showInstaller(apk:File) {
        if (!apk.exists()) return
        try {
            val uri=FileProvider.getUriForFile(this,packageName+".sofix.updates",apk)
            val intent=Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri,"application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }catch(e:Exception){
            message.text="Не удалось открыть установку: "+e.message
        }
    }
}

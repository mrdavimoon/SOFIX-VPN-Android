#!/usr/bin/env python3
"""SOFIX native VPN UI overlay for pinned GPLv3 v2rayNG source."""
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
overlay = Path(__file__).resolve().parent
app = root / "V2rayNG" / "app"
assert (root / "LICENSE").exists(), "GPLv3 source required"
assert (app / "build.gradle.kts").exists()

def change(path, old, new):
    s = path.read_text()
    if s.count(old) != 1:
        raise AssertionError(f"Expected exactly one match in {path}: {old[:70]}")
    path.write_text(s.replace(old, new))

build = app / "build.gradle.kts"
change(build, 'applicationId = "com.v2ray.ang"', 'applicationId = "com.sofix.vpn"')
change(build, 'versionCode = 752', 'versionCode = 755')
change(build, 'versionName = "2.3.12"', 'versionName = "0.5.0-beta.1"')

manifest = app / "src/main/AndroidManifest.xml"
s = manifest.read_text()
s = s.replace('android:allowBackup="true"', 'android:allowBackup="false"')
s = s.replace('android:label="@string/app_name"', 'android:label="SOFIX VPN"')
s = s.replace('    <uses-permission android:name="android.permission.INTERNET" />',
              '    <uses-permission android:name="android.permission.INTERNET" />\n'
              '    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />')
anchor = '        <activity\n            android:name=".ui.main.MainActivity"'
assert s.count(anchor) == 1
s = s.replace(anchor, '        <activity android:name=".ui.sofix.SofixUpdateActivity" android:exported="false" />\n' + anchor)
anchor = '            <meta-data\n                android:name="android.app.shortcuts"'
assert s.count(anchor) == 1
filters = """            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" android:host="lk.sofixconnection.ru" android:path="/mobile/return" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="sofixvpn" android:host="home" />
            </intent-filter>
"""
s = s.replace(anchor, filters + anchor)
anchor = '        <service\n'
assert s.count(anchor) >= 1
s = s.replace(anchor, '    <provider\n        android:name="androidx.core.content.FileProvider"\n        android:authorities="${applicationId}.sofix.updates"\n        android:exported="false"\n        android:grantUriPermissions="true">\n        <meta-data android:name="android.support.FILE_PROVIDER_PATHS"\n            android:resource="@xml/sofix_update_paths" />\n    </provider>\n'+anchor, 1)
manifest.write_text(s)
shutil.copy2(overlay / "sofix_update_paths.xml", app / "src/main/res/xml/sofix_update_paths.xml")

main = app / "src/main/java/com/v2ray/ang/ui/main/MainActivity.kt"
s = main.read_text()
anchor = 'import com.v2ray.ang.AngApplication\n'
assert s.count(anchor) == 1
s = s.replace(anchor, anchor + """import com.v2ray.ang.ui.sofix.SofixHomeScreen
import com.v2ray.ang.ui.sofix.SofixGuest
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.os.Handler
import android.os.Looper
import android.app.AlertDialog
import com.v2ray.ang.ui.sofix.SofixUpdateActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
""")
anchor = '        mainViewModel.onAction(MainAction.Initialize)'
assert s.count(anchor) == 1
s = s.replace(anchor, """        // Home is always available; Telegram login must never block VPN.
""" + anchor)

start = s.index('    @Composable\n    override fun ScreenContent() {')
end = s.index('    private fun shareToClipboard(', start)
screen = """    @Composable
    override fun ScreenContent() {
        BackHandler { moveTaskToBack(false) }
        val state by mainViewModel.uiState.collectAsStateWithLifecycle()
        SofixHomeScreen(
            connected = state.isRunning,
            configured = !state.selectedGuid.isNullOrBlank(),
            status = mainViewModel.formatStatus(state.status),
            onPower = {
                if (!SofixGuest.hasPaid(this) && SofixGuest.trialLeft(this)<=0) showTrialOffer()
                else {
                    handleFabAction()
                    if (SofixGuest.started(this) && !SofixGuest.verified(this)) scheduleVerifyTelegram()
                }
            },
            onAccount = { section -> openSofixMiniApp(section) },
            onRefresh = { mainViewModel.onAction(MainAction.UpdateSubscriptions) },
            onTest = { handleLayoutTestClick() },
            onVpnSettings = { navigateTo(MainDestination.Settings) },
            onUpdates = { startActivity(Intent(this, SofixUpdateActivity::class.java)) },
            hasSubscription = SofixGuest.hasPaid(this) || SofixGuest.trialLeft(this)>0,
            trialSeconds = trialSeconds,
            accountLinked = accountLinked,
            guestBusy = guestBusy,
            guestNote = guestNote,
            onSubscribe = { showTrialOffer() },
            onLinkAccount = { openLinkAfterTrial() },
        )
    }

"""
miniapp = """    private fun openSofixMiniApp(section: String) {
        val allowed = setOf("account", "wallet", "renew", "auto", "devices")
        if (section !in allowed) return
        val link = Uri.parse("https://t.me/SofixVpnBot?startapp=sofix_" + section)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, link).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: Exception) {
            toastError(R.string.toast_failure)
        }
    }

"""
s = s[:start] + screen + miniapp + s[end:]
anchor = '    private fun shareToClipboard(guid: String): Boolean ='
assert s.count(anchor) == 1
helper = r"""    private var guestBusy by mutableStateOf(false)
    private var guestNote by mutableStateOf("")
    private var trialSeconds by mutableStateOf(0L)
    private var accountLinked by mutableStateOf(false)
    private val guestHandler=Handler(Looper.getMainLooper())

    override fun onResume() {
        super.onResume()
        trialSeconds=SofixGuest.trialLeft(this)
        accountLinked=SofixGuest.claimed(this)
        if (SofixGuest.started(this)) {
            Thread {
                try {
                    val data=SofixGuest.trialStatus(this)
                    runOnUiThread {
                        trialSeconds=SofixGuest.trialLeft(this)
                        accountLinked=data.optBoolean("linked")
                        if (data.optString("state")=="ready") {
                            mainViewModel.onAction(MainAction.RefreshGroups)
                            guestNote="Подписка загружена. Можно подключаться."
                        }
                    }
                } catch (_:Exception) {}
            }.start()
        }
        scheduleTrialClock()
    }

    override fun onPause() {
        guestHandler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun scheduleTrialClock() {
        guestHandler.removeCallbacksAndMessages(null)
        guestHandler.postDelayed(object:Runnable {
            override fun run() {
                trialSeconds=SofixGuest.trialLeft(this@MainActivity)
                if(trialSeconds==0L && SofixGuest.started(this@MainActivity)
                    && !SofixGuest.hasPaid(this@MainActivity)
                    && mainViewModel.uiState.value.isRunning) {
                    LauncherManager.stopService(this@MainActivity)
                    guestNote="15 минут закончились. Оформите подписку."
                }
                guestHandler.postDelayed(this,1000)
            }
        },1000)
    }

    private fun showTrialOffer() {
        if(guestBusy)return
        if(SofixGuest.hasPaid(this)) {
            openSofixMiniApp("renew")
            return
        }
        if(SofixGuest.started(this)) {
            openLinkAfterTrial()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("15 минут VPN бесплатно")
            .setMessage("Сначала включим пробный VPN без регистрации. Дождёмся интернета и проверим Telegram, а уже потом предложим привязать телефон к аккаунту SOFIX. Пробный доступ выдаётся один раз.")
            .setPositiveButton("Включить на 15 минут") { _,_ -> startGuestTrial() }
            .setNeutralButton("Без пробного — Telegram") { _,_ -> openSofixMiniApp("account") }
            .setNegativeButton("Отмена",null)
            .show()
    }

    private fun startGuestTrial() {
        if(guestBusy)return
        guestBusy=true
        guestNote="Выдаём 15 минут VPN…"
        Thread {
            try {
                SofixGuest.startTrial(this)
                runOnUiThread {
                    trialSeconds=SofixGuest.trialLeft(this)
                    guestNote="Получен пробный доступ. Запускаем VPN…"
                    mainViewModel.onAction(MainAction.RefreshGroups)
                    startTrialTunnel(0)
                }
            } catch (e:Exception) {
                runOnUiThread {
                    guestBusy=false
                    guestNote=e.message?:"Не удалось включить пробный VPN"
                }
            }
        }.start()
    }

    private fun startTrialTunnel(attempt:Int) {
        if(trialSeconds<=0) { guestBusy=false; return }
        if(mainViewModel.uiState.value.isRunning) {
            guestBusy=false
            scheduleVerifyTelegram()
            return
        }
        if(mainViewModel.uiState.value.selectedGuid.isNullOrEmpty()) {
            if(attempt<12)guestHandler.postDelayed({ startTrialTunnel(attempt+1) },700)
            else {guestBusy=false;guestNote="Подписка получена, но сервер не загрузился. Нажмите обновление сервера."}
            return
        }
        guestBusy=false
        handleFabAction()
        scheduleVerifyTelegram()
    }

    private fun scheduleVerifyTelegram(attempt:Int=0) {
        if(!SofixGuest.started(this) || SofixGuest.verified(this) || SofixGuest.trialLeft(this)<=0)return
        guestHandler.postDelayed({
            Thread {
                val ok=SofixGuest.checkVpnAndTelegram(this)
                runOnUiThread {
                    if(ok) {
                        SofixGuest.markVerified(this)
                        guestNote="VPN работает. Telegram доступен! Привязываем аккаунт…"
                        openLinkAfterTrial()
                    } else if(attempt<18 && SofixGuest.trialLeft(this)>0) {
                        guestNote="Проверяем VPN и доступность Telegram…"
                        scheduleVerifyTelegram(attempt+1)
                    } else {
                        guestNote="VPN выдан, но пока не удалось проверить Telegram. Когда интернет заработает, нажмите «Продолжить в Telegram»."
                    }
                }
            }.start()
        },2200)
    }

    private fun openLinkAfterTrial() {
        if(SofixGuest.claimed(this)) {
            openSofixMiniApp("account")
            return
        }
        if(!SofixGuest.started(this)) {
            showTrialOffer();return
        }
        if(guestBusy)return
        guestBusy=true
        guestNote="Открываем привязку через Telegram…"
        Thread {
            try {
                val link=SofixGuest.beginLink(this)
                runOnUiThread {
                    guestBusy=false
                    guestNote="Telegram откроет SOFIX Mini App. Подтвердите привязку без кода."
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link)).addCategory(Intent.CATEGORY_BROWSABLE))
                    } catch (e:Exception) {
                        guestNote="Не удалось открыть Telegram. Попробуйте снова."
                    }
                }
            } catch(e:Exception) {
                runOnUiThread {guestBusy=false;guestNote=e.message?:"Не удалось открыть Mini App"}
            }
        }.start()
    }

"""
s = s.replace(anchor, helper + anchor)
main.write_text(s)

dest = app / "src/main/java/com/v2ray/ang/ui/sofix"
dest.mkdir(parents=True, exist_ok=True)
for name in ("SofixHomeScreen.kt", "SofixUpdateActivity.kt", "SofixGuest.kt"):
    shutil.copy2(overlay / name, dest / name)
print("SOFIX 0.4 native UI, verified updater, VPN controls installed")

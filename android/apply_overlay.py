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
change(build, 'versionCode = 752', 'versionCode = 761')
change(build, 'versionName = "2.3.12"', 'versionName = "0.8.3-beta.1"')

manifest = app / "src/main/AndroidManifest.xml"
s = manifest.read_text()
s = s.replace('android:allowBackup="true"', 'android:allowBackup="false"')
s = s.replace('android:label="@string/app_name"', 'android:label="SOFIX VPN"')
s = s.replace('    <uses-permission android:name="android.permission.INTERNET" />',
              '    <uses-permission android:name="android.permission.INTERNET" />\n'
              '    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />')
anchor = '        <activity\n            android:name=".ui.main.MainActivity"'
assert s.count(anchor) == 1

s = s.replace(anchor, '        <activity android:name=".ui.sofix.SofixRoutingActivity" android:exported="false" />\n'
                      + '        <activity android:name=".ui.sofix.SofixDiagnosticsActivity" android:exported="false" />\n'
                      + '        <activity android:name=".ui.sofix.SofixDemoActivity" android:exported="false" />\n'
                      + '        <activity android:name=".ui.sofix.SofixSimpleSettingsActivity" android:exported="false" />\n'
                      + '        <activity android:name=".ui.sofix.SofixUpdateActivity" android:exported="false" />\n' + anchor)
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
s = s.replace(anchor, anchor + """import com.v2ray.ang.ui.sofix.SofixLegacyPair
import com.v2ray.ang.ui.sofix.SofixPaidPair
import com.v2ray.ang.ui.sofix.SofixApiException
import com.v2ray.ang.ui.sofix.SofixHomeScreen
import com.v2ray.ang.ui.sofix.SofixUpdateActivity
import com.v2ray.ang.ui.sofix.SofixSimpleSettingsActivity
import com.v2ray.ang.ui.sofix.SofixTrialManager
import com.v2ray.ang.ui.sofix.SofixHealth
import com.v2ray.ang.ui.sofix.SofixSubscription
import com.v2ray.ang.ui.sofix.SofixDiagnosticsActivity
import com.v2ray.ang.ui.sofix.SofixRuleUpdates
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import android.provider.Settings
""")
anchor = '        mainViewModel.onAction(MainAction.Initialize)'
assert s.count(anchor) == 1
s = s.replace(anchor, anchor + "\n        startTrialPolling()\n        startHealthPolling()\n        bootEnabled = MmkvManager.decodeStartOnBoot()")

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
                if (legacyRevoked && !trialPaid) beginPaidPair()
                else if (!SofixLegacyPair.paired(this) && !trialPaid && trialMinutesLeft <= 0) showSubscriptionOffer()
                else handleFabAction()
            },
            onAccount = { section ->
                if (!SofixLegacyPair.paired(this) && !trialLinked && !trialPaid)
                    showSubscriptionOffer()
                else openSofixMiniApp(section)
            },
            onRefresh = { mainViewModel.onAction(MainAction.UpdateSubscriptions) },
            onTest = { handleLayoutTestClick() },
            onVpnSettings = { startActivity(Intent(this, SofixSimpleSettingsActivity::class.java)) },
            onUpdates = { startActivity(Intent(this, SofixUpdateActivity::class.java)) },
            hasEntitlement = (SofixLegacyPair.paired(this) && !legacyRevoked) || trialPaid || trialMinutesLeft > 0,
            isTrial = !trialPaid && !(SofixLegacyPair.paired(this) && !legacyRevoked) && trialMinutesLeft > 0,
            trialMinutes = trialMinutesLeft,
            linked = trialLinked,
            onboardingMessage = trialHint,
            onSubscribe = { showSubscriptionOffer() },
            onLink = { verifyAndLinkTelegram() },
            onRestore = { beginPaidPair() },
            showRestore = legacyRevoked || (!SofixLegacyPair.paired(this) && !trialPaid) ||
                SofixPaidPair.isPending(this),
            recoverRequired = legacyRevoked,
            restoring = paidPairBusy,
            autoConnectOnBoot = bootEnabled,
            onAutoConnectOnBoot = { enabled ->
                MmkvManager.encodeStartOnBoot(enabled)
                bootEnabled = enabled
            },
            networkState = connectionStatus,
            subscriptionLabel = subscriptionLabel,
            onDiagnostics = { startActivity(Intent(this, SofixDiagnosticsActivity::class.java)) },
            onSystemVpnSettings = {
                try { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                catch (_:Exception) { trialHint="Настройки VPN находятся в системных настройках Android." }
            },
        )
        if(showPaidPairDialog) {
            AlertDialog(
                onDismissRequest = { showPaidPairDialog=false },
                title = { Text("Привязка к действующей подписке") },
                text = { Text("Ваш код: " + paidPairPin +
                    "\\n\\n1. Запомните шесть цифр.\\n" +
                    "2. Откройте Telegram и подтвердите этот телефон.\\n" +
                    "3. Вернитесь сюда. Повторная оплата не нужна.") },
                confirmButton = {
                    TextButton(onClick = { openPaidPairTelegram() }) {
                        Text("Открыть Telegram")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPaidPairDialog=false; resumePaidPair() }) {
                        Text("Проверить привязку")
                    }
                },
            )
        }
        if(offerTrial) {
            AlertDialog(
                onDismissRequest = { offerTrial = false },
                title = { Text("Попробуйте SOFIX VPN") },
                text = { Text("Сначала включим бесплатный VPN на 15 минут. Telegram пока не нужен. После проверки интернета сможете привязать аккаунт без кода.") },
                confirmButton = {
                    TextButton(onClick = { beginFreeTrial() }) {
                        Text("Включить на 15 минут")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        offerTrial=false
                        openSofixMiniApp("account")
                    }) { Text("Перейти без пробного доступа") }
                },
            )
        }
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
trial = (overlay / "SofixTrialMain.kt.txt").read_text()
trial = trial.replace(
    '    private var offerTrial by mutableStateOf(false)',
    '    private var bootEnabled by mutableStateOf(false)\n    private var offerTrial by mutableStateOf(false)'
)
assert "private var bootEnabled by mutableStateOf(false)" in trial
s = s[:start] + screen + miniapp + trial + s[end:]
main.write_text(s)

boot_receiver = app / "src/main/java/com/v2ray/ang/receiver/BootReceiver.kt"
boot_source = boot_receiver.read_text()
anchor = 'import com.v2ray.ang.handler.SubscriptionUpdater\n'
assert boot_source.count(anchor) == 1
boot_source = boot_source.replace(anchor, anchor +
    'import com.v2ray.ang.ui.sofix.SofixTrialManager\n' +
    'import com.v2ray.ang.ui.sofix.SofixLegacyPair\n')
anchor = '        LogUtil.i(AppConfig.TAG, "BootReceiver: Starting V2Ray service")'
assert boot_source.count(anchor) == 1
boot_source = boot_source.replace(anchor, """        if (SofixTrialManager.token(context) != null
            && !SofixTrialManager.paid(context)
            && !SofixLegacyPair.paired(context)
            && SofixTrialManager.expiresAt(context) <= System.currentTimeMillis() / 1000L) {
            LogUtil.i(AppConfig.TAG, "BootReceiver: Anonymous trial expired; skip autostart")
            return
        }

""" + anchor)
boot_receiver.write_text(boot_source)

dest = app / "src/main/java/com/v2ray/ang/ui/sofix"
dest.mkdir(parents=True, exist_ok=True)
for name in ("SofixPaidPair.kt", "SofixLegacyPair.kt", "SofixHomeScreen.kt", "SofixUpdateActivity.kt", "SofixUpdateScreen.kt", "SofixSimpleSettingsActivity.kt", "SofixTrialManager.kt", "SofixRouting.kt", "SofixRuleUpdates.kt", "SofixRoutingActivity.kt", "SofixDiagnosticsActivity.kt", "SofixSubscription.kt", "SofixDemoActivity.kt"):
    shutil.copy2(overlay / name, dest / name)
core=app / "src/main/java/com/v2ray/ang/core/CoreConfigManager.kt"
core_source=core.read_text()
# The default v2rayNG preset is not a manually customized ruleset.
# SOFIX's smart rules supersede this factory preset, while custom user
# expert rules stay available for explicit opt-in and rollback.
legacy='        rulesetItems?.forEach { key ->\n            appendRoutingUserRule(configContext, key, v2rayConfig, policyGroupBalancerTags)\n        }'
managed='        if (!com.v2ray.ang.ui.sofix.SofixRouting.managedActive(configContext.context)) {\n            rulesetItems?.forEach { key ->\n                appendRoutingUserRule(configContext, key, v2rayConfig, policyGroupBalancerTags)\n            }\n        }'
assert core_source.count(legacy)==1
core_source=core_source.replace(legacy,managed)
anchor="        configureRootModeDns(v2rayConfig)\n"
assert core_source.count(anchor)==1
core_source=core_source.replace(anchor,anchor+"        com.v2ray.ang.ui.sofix.SofixRouting.apply(configContext.context,v2rayConfig)\n")
core.write_text(core_source)
print("SOFIX 0.8 routing, diagnostics, account status and onboarding demo")

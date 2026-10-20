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
change(build, 'versionCode = 752', 'versionCode = 754')
change(build, 'versionName = "2.3.12"', 'versionName = "0.4.0-beta.1"')

manifest = app / "src/main/AndroidManifest.xml"
s = manifest.read_text()
s = s.replace('android:allowBackup="true"', 'android:allowBackup="false"')
s = s.replace('android:label="@string/app_name"', 'android:label="SOFIX VPN"')
s = s.replace('    <uses-permission android:name="android.permission.INTERNET" />',
              '    <uses-permission android:name="android.permission.INTERNET" />\n'
              '    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />')
anchor = '        <activity\n            android:name=".ui.main.MainActivity"'
assert s.count(anchor) == 1
s = s.replace(anchor, '        <activity android:name=".ui.sofix.SofixPairActivity" android:exported="false" />\n' + anchor)
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
s = s.replace(anchor, anchor + """import com.v2ray.ang.ui.sofix.SofixPairActivity
import com.v2ray.ang.ui.sofix.SofixHomeScreen
import com.v2ray.ang.ui.sofix.SofixUpdateActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
""")
anchor = '        mainViewModel.onAction(MainAction.Initialize)'
assert s.count(anchor) == 1
s = s.replace(anchor, """        if (!SofixPairActivity.paired(this)) {
            startActivity(Intent(this, SofixPairActivity::class.java))
            finish()
            return
        }
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
            onPower = { handleFabAction() },
            onAccount = { section -> openSofixMiniApp(section) },
            onRefresh = { mainViewModel.onAction(MainAction.UpdateSubscriptions) },
            onTest = { handleLayoutTestClick() },
            onVpnSettings = { navigateTo(MainDestination.Settings) },
            onUpdates = { startActivity(Intent(this, SofixUpdateActivity::class.java)) },
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
main.write_text(s)

dest = app / "src/main/java/com/v2ray/ang/ui/sofix"
dest.mkdir(parents=True, exist_ok=True)
for name in ("SofixPairActivity.kt", "SofixHomeScreen.kt", "SofixUpdateActivity.kt"):
    shutil.copy2(overlay / name, dest / name)
print("SOFIX 0.4 native UI, verified updater, VPN controls installed")

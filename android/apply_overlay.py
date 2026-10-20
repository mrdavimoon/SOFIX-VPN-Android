#!/usr/bin/env python3
"""Apply SOFIX's Android UI overlay to the pinned GPLv3 v2rayNG checkout."""
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
overlay = Path(__file__).resolve().parent
app = root / "V2rayNG" / "app"
assert (app / "build.gradle.kts").exists()
assert (root / "LICENSE").exists()

build = app / "build.gradle.kts"
s = build.read_text()
old = 'applicationId = "com.v2ray.ang"'
assert s.count(old) == 1
s = s.replace(old, 'applicationId = "com.sofix.vpn"')
build.write_text(s)

manifest = app / "src/main/AndroidManifest.xml"
s = manifest.read_text()
s = s.replace('android:allowBackup="true"', 'android:allowBackup="false"')
s = s.replace('android:label="@string/app_name"', 'android:label="SOFIX VPN"')
anchor = '        <activity\n            android:name=".ui.main.MainActivity"'
assert s.count(anchor) == 1
s = s.replace(anchor, '\n'.join([
    '        <activity android:name=".ui.sofix.SofixPairActivity" android:exported="false" />',
    anchor,
]))
anchor = '            <meta-data\n                android:name="android.app.shortcuts"'
assert s.count(anchor) == 1
filters = '\n'.join([
    '            <intent-filter android:autoVerify="true">',
    '                <action android:name="android.intent.action.VIEW" />',
    '                <category android:name="android.intent.category.DEFAULT" />',
    '                <category android:name="android.intent.category.BROWSABLE" />',
    '                <data android:scheme="https" android:host="lk.sofixconnection.ru" android:path="/mobile/return" />',
    '            </intent-filter>',
    '            <intent-filter>',
    '                <action android:name="android.intent.action.VIEW" />',
    '                <category android:name="android.intent.category.DEFAULT" />',
    '                <category android:name="android.intent.category.BROWSABLE" />',
    '                <data android:scheme="sofixvpn" android:host="home" />',
    '            </intent-filter>',
    '',
])
s = s.replace(anchor, filters + anchor)
manifest.write_text(s)

main = app / "src/main/java/com/v2ray/ang/ui/main/MainActivity.kt"
s = main.read_text()
anchor = 'import com.v2ray.ang.AngApplication\n'
assert s.count(anchor) == 1
imports = '\n'.join([
    'import com.v2ray.ang.ui.sofix.SofixPairActivity',
    'import com.v2ray.ang.ui.sofix.SofixAccountBar',
    'import android.net.Uri',
    'import androidx.compose.foundation.layout.Column',
    'import androidx.compose.foundation.layout.Box',
    'import androidx.compose.foundation.layout.fillMaxSize',
    'import androidx.compose.foundation.layout.weight',
    'import androidx.compose.ui.Modifier',
    '',
])
s = s.replace(anchor, anchor + imports)
anchor = '        mainViewModel.onAction(MainAction.Initialize)'
assert s.count(anchor) == 1
gate = '\n'.join([
    '        if (!SofixPairActivity.paired(this)) {',
    '            startActivity(Intent(this, SofixPairActivity::class.java))',
    '            finish()',
    '            return',
    '        }',
    '',
])
s = s.replace(anchor, gate + anchor)
# Preserve the upstream VPN UI and its connect/disconnect actions.
anchor = '        MainScreen(\n'
assert s.count(anchor) == 1
top = '\n'.join([
    '        Column(modifier = Modifier.fillMaxSize()) {',
    '            SofixAccountBar(openAccount = { section -> openSofixMiniApp(section) })',
    '            Box(modifier = Modifier.weight(1f)) {',
    '',
])
s = s.replace(anchor, top + anchor)
anchor = '        )\n    }\n\n    private fun shareToClipboard'
assert s.count(anchor) == 1
s = s.replace(anchor, '\n'.join([
    '        )',
    '            }',
    '        }',
    '    }',
    '',
    '    private fun shareToClipboard',
]))
anchor = '    private fun shareToClipboard(guid: String): Boolean ='
assert s.count(anchor) == 1
method = '\n'.join([
    '    private fun openSofixMiniApp(section: String) {',
    '        val allowed = setOf("account", "wallet", "renew", "auto", "devices")',
    '        if (section !in allowed) return',
    '        val link = Uri.parse("https://t.me/SofixVpnBot?startapp=sofix_" + section)',
    '        try {',
    '            startActivity(Intent(Intent.ACTION_VIEW, link).addCategory(Intent.CATEGORY_BROWSABLE))',
    '        } catch (_: Exception) {',
    '            toastError(R.string.toast_failure)',
    '        }',
    '    }',
    '',
])
s = s.replace(anchor, method + anchor)
main.write_text(s)

dest = app / "src/main/java/com/v2ray/ang/ui/sofix"
dest.mkdir(parents=True, exist_ok=True)
for name in ("SofixPairActivity.kt", "SofixAccountBar.kt"):
    shutil.copy2(overlay / name, dest / name)
print("SOFIX Android interface and bidirectional Mini App navigation applied.")

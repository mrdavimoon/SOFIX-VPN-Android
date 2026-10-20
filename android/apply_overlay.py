#!/usr/bin/env python3
"""Apply SOFIX identity and pairing to the pinned GPLv3 v2rayNG source checkout.

Source for the complete derivative is published with the APK; copyright notices
and the original GPL-3.0 license must be preserved.
"""
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
overlay = Path(__file__).resolve().parent
app = root / "V2rayNG" / "app"
assert (app / "build.gradle.kts").exists(), "Unsupported upstream checkout"
assert (root / "LICENSE").exists(), "Keep upstream GPLv3 license"

build = app / "build.gradle.kts"
s = build.read_text()
assert s.count('applicationId = "com.v2ray.ang"') == 1
s = s.replace('applicationId = "com.v2ray.ang"', 'applicationId = "com.sofix.vpn"')
build.write_text(s)

manifest = app / "src/main/AndroidManifest.xml"
s = manifest.read_text()
s = s.replace('android:allowBackup="true"', 'android:allowBackup="false"')
s = s.replace('android:label="@string/app_name"', 'android:label="SOFIX VPN"')
needle = '        <activity\n            android:name=".ui.main.MainActivity"'
assert s.count(needle) == 1
s = s.replace(needle, '''        <activity
            android:name=".ui.sofix.SofixPairActivity"
            android:exported="false" />
''' + needle)
manifest.write_text(s)

main = app / "src/main/java/com/v2ray/ang/ui/main/MainActivity.kt"
s = main.read_text()
needle = 'import com.v2ray.ang.AngApplication\n'
assert s.count(needle) == 1
s = s.replace(needle, needle + 'import com.v2ray.ang.ui.sofix.SofixPairActivity\n')
needle = '        mainViewModel.onAction(MainAction.Initialize)'
assert s.count(needle) == 1
gate = (
    '        if (!SofixPairActivity.paired(this)) {\n'
    '            startActivity(Intent(this, SofixPairActivity::class.java))\n'
    '            finish()\n'
    '            return\n'
    '        }\n'
)
s = s.replace(needle, gate + needle)
main.write_text(s)

dest = app / "src/main/java/com/v2ray/ang/ui/sofix"
dest.mkdir(parents=True, exist_ok=True)
shutil.copy2(overlay / "SofixPairActivity.kt", dest / "SofixPairActivity.kt")
print("SOFIX Android patch applied to pinned upstream source")

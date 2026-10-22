"""Safety checks for sideloaded SOFIX update pipeline."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]

class UpdateSafetyTests(unittest.TestCase):
    def test_manifest_overlay_limits_installer_provider(self):
        text=(ROOT/"android/apply_overlay.py").read_text()
        self.assertIn("SofixUpdateActivity",text)
        self.assertIn("sofix_update_paths.xml",text)
        self.assertIn("REQUEST_INSTALL_PACKAGES",text)
        self.assertIn("grantUriPermissions",text)
        self.assertIn("onUpdates",text)
        self.assertIn("versionCode = 761",text)
        self.assertIn("versionName = \"0.8.3-beta.1\"",text)

    def test_updater_validates_download_before_installation(self):
        text=(ROOT/"android/SofixUpdateActivity.kt").read_text()
        for phrase in ("SHA-256", "packageName", "FileProvider.getUriForFile",
                       "canRequestPackageInstalls", "ACTION_MANAGE_UNKNOWN_APP_SOURCES",
                       "FLAG_GRANT_READ_URI_PERMISSION", "getPackageArchiveInfo",
                       "SOFIX-VPN-Android-arm64-beta.apk", "SHA256SUMS"):
            self.assertIn(phrase,text)
        self.assertIn("Github",text.replace("github","Github"))

    def test_ci_keeps_signing_key_out_of_public_repo(self):
        text=(ROOT/".github/workflows/build.yml").read_text()
        self.assertIn("assembleFdroidRelease",text)
        self.assertIn("unsigned.apk",text)
        self.assertNotIn("gh release create",text)
        self.assertNotIn("SOFIX_APK_KEYSTORE",text)

    def test_update_button_is_visible(self):
        text=(ROOT/"android/SofixHomeScreen.kt").read_text()
        self.assertIn("ПРОВЕРИТЬ ОБНОВЛЕНИЯ",text)
        self.assertIn("onUpdates()",text)

    def test_release_notes_exist(self):
        self.assertGreater((ROOT/"release-notes/v0.8.3-beta.1.md").stat().st_size,1000)

if __name__=="__main__":
    unittest.main()

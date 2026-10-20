"""Regression checks for SOFIX native boot / always-on VPN settings."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

class BootBehaviorTests(unittest.TestCase):
    def test_boot_flag_bound_to_original_v2rayng_setting(self):
        source=(ROOT/"android/apply_overlay.py").read_text()
        self.assertIn("MmkvManager.decodeStartOnBoot()", source)
        settings=(ROOT/"android/SofixSimpleSettingsActivity.kt").read_text()
        self.assertIn("MmkvManager.encodeStartOnBoot(it)", settings)
        self.assertIn("autostart=it", settings)
        self.assertIn('0.8.1-beta.1', source)
        self.assertIn('versionCode = 759', source)

    def test_expired_trial_skips_boot(self):
        src=(ROOT/"android/apply_overlay.py").read_text()
        for item in ("BootReceiver.kt", "SofixTrialManager.expiresAt(context)",
                     "!SofixTrialManager.paid(context)", "!SofixLegacyPair.paired(context)",
                     "Anonymous trial expired"):
            self.assertIn(item, src)

    def test_user_controls_and_android_system_settings(self):
        overlay=(ROOT/"android/apply_overlay.py").read_text()
        ui=(ROOT/"android/SofixSimpleSettingsActivity.kt").read_text()
        for text in ("Включать VPN после перезагрузки", "Постоянная VPN", "Switch("):
            self.assertIn(text,ui)
        self.assertIn("Settings.ACTION_VPN_SETTINGS",overlay)
        self.assertIn("SofixSimpleSettingsActivity",overlay)

    def test_release_notes(self):
        self.assertGreater((ROOT/"release-notes/v0.6.0-beta.1.md").stat().st_size,650)

if __name__=="__main__":
    unittest.main()

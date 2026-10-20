"""Checks that Android first launch is a VPN screen, not a PIN/Telegram gate."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

class TrialFirstTests(unittest.TestCase):
    def test_no_six_digit_android_pairing(self):
        overlay=(ROOT/"android/apply_overlay.py").read_text()
        home=(ROOT/"android/SofixHomeScreen.kt").read_text()
        guest=(ROOT/"android/SofixGuest.kt").read_text()
        self.assertNotIn("SofixPairActivity",overlay)
        self.assertNotIn("pair_pin",guest+home)
        self.assertIn("15 минут",overlay)
        self.assertIn("НЕТ ПОДПИСКИ",home)
        self.assertIn("ОФОРМИТЬ ПОДПИСКУ",home)
        self.assertIn("SofixGuest.startTrial",overlay)

    def test_trial_vpn_before_telegram(self):
        overlay=(ROOT/"android/apply_overlay.py").read_text()
        self.assertIn("SofixGuest.checkVpnAndTelegram",overlay)
        self.assertIn("scheduleVerifyTelegram",overlay)
        self.assertIn("SofixGuest.beginLink",overlay)
        self.assertIn("SofixGuest.trialLeft",overlay)
        self.assertIn("handleFabAction()",overlay)
        self.assertIn("SofixHomeScreen(",overlay)
        self.assertIn('versionName = "0.5.0-beta.1"',overlay)

    def test_temporary_trial_and_no_web_identity_before_telegram(self):
        code=(ROOT/"android/SofixGuest.kt").read_text()
        self.assertIn('"/mobile/api/trial/start"',code)
        self.assertIn('"/mobile/api/trial/link/start"',code)
        self.assertIn("ConnectivityManager",code)
        self.assertIn("TRANSPORT_VPN",code)
        self.assertIn("https://telegram.org",code)
        self.assertNotIn("Telegram.WebApp",code)

    def test_modern_ui_keeps_real_vpn_functions(self):
        overlay=(ROOT/"android/apply_overlay.py").read_text()
        for item in ("MainAction.RefreshGroups","MainAction.UpdateSubscriptions",
                     "MainDestination.Settings","handleLayoutTestClick()",
                     "SofixUpdateActivity","onUpdates"):
            self.assertIn(item,overlay)

if __name__=="__main__":
    unittest.main()

"""Regressions for VPN-first SOFIX Android onboarding."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
UI=(ROOT/"android/SofixHomeScreen.kt").read_text()
OVERLAY=(ROOT/"android/apply_overlay.py").read_text()
TRIAL=(ROOT/"android/SofixTrialManager.kt").read_text()
FLOW=(ROOT/"android/SofixTrialMain.kt.txt").read_text()

class TrialFirstOnboarding(unittest.TestCase):
    def test_no_forced_telegram_on_launch(self):
        self.assertNotIn("startActivity(Intent(this, SofixPairActivity::class.java))",OVERLAY)
        self.assertFalse((ROOT/"android/SofixPairActivity.kt").exists())
        self.assertNotIn("pin_hash",TRIAL)
        self.assertIn('versionName = "0.8.2-beta.1"',OVERLAY)
    def test_home_starts_without_subscription(self):
        self.assertIn("НЕТ ПОДПИСКИ",UI)
        self.assertIn("ОФОРМИТЬ ПОДПИСКУ",UI)
        self.assertIn("Включить на 15 минут",OVERLAY)
        self.assertIn("ПРОВЕРИТЬ ОБНОВЛЕНИЯ",UI)
        self.assertIn("onPower()",UI)
    def test_trial_before_telegram(self):
        self.assertIn('/mobile/api/trial/start',TRIAL)
        self.assertIn("requestServiceStart()",FLOW)
        self.assertIn("SofixTrialManager.connectivityCheck()",FLOW)
        self.assertIn("SofixTrialManager.beginLink",FLOW)
        self.assertLess(FLOW.index("SofixTrialManager.start"),FLOW.index("SofixTrialManager.beginLink"))
        self.assertIn("https://t.me/SofixVpnBot",TRIAL)
    def test_old_installations_can_remain_paired(self):
        old=(ROOT/"android/SofixLegacyPair.kt").read_text()
        self.assertIn('getString("token", null)',old)
        self.assertIn('refreshLegacyDeviceState()',FLOW)
        self.assertIn("SofixLegacyPair.paired(this)",OVERLAY)
    def test_vpn_engine_and_settings_preserved(self):
        for symbol in ("handleFabAction()","MainAction.UpdateSubscriptions",
                       "collectAsStateWithLifecycle()","SofixSimpleSettingsActivity"):
            self.assertIn(symbol,OVERLAY)
        self.assertIn("MainAction.RefreshGroups",FLOW)

if __name__=="__main__":unittest.main()

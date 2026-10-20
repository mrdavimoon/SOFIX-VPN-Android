"""Static regression guards for the SOFIX v0.3 native build overlay.

These checks run before heavy Android/NDK compilation and complement,
but never replace, real device tests of Telegram and VpnService.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PAIR = ROOT / "android" / "SofixPairActivity.kt"
UI = ROOT / "android" / "SofixHomeScreen.kt"
OVERLAY = ROOT / "android" / "apply_overlay.py"


class PairingRegression(unittest.TestCase):
    def test_polling_does_not_depend_on_completed_pairing(self):
        code = PAIR.read_text()
        self.assertIn("if (!token().isNullOrBlank()) check()", code)
        self.assertNotIn("if (paired(this@SofixPairActivity)) check()", code)

    def test_existing_device_token_is_restored_after_telegram(self):
        code = PAIR.read_text()
        self.assertIn('prefs().getString("telegram_url", null)', code)
        self.assertIn('prefs().getString("pair_pin", "")', code)
        self.assertIn('if (!token().isNullOrBlank()) {', code)
        self.assertIn('prefs().edit().putBoolean("pair_complete", true)', code)

    def test_no_premature_login_until_subscription_is_ready(self):
        code = PAIR.read_text()
        self.assertIn('getBoolean("pair_complete", false) && !p.getString(TOKEN, null).isNullOrBlank()', code)
        self.assertIn('val url=r.getString("subscription_url")', code)
        self.assertLess(code.index('val url=r.getString("subscription_url")'),
                        code.index('prefs().edit().putBoolean("pair_complete", true)'))

    def test_native_screen_preserves_vpn_actions(self):
        ui = UI.read_text()
        overlay = OVERLAY.read_text()
        for needed in ("onPower()", 'onAccount("account")', 'onAccount("devices")',
                       'onAccount("renew")', 'onVpnSettings()', "onRefresh()"):
            self.assertIn(needed, ui)
        for needed in ("handleFabAction()", "handleLayoutTestClick()", "MainAction.UpdateSubscriptions",
                       "SofixHomeScreen(", "collectAsStateWithLifecycle()", "MainDestination.Settings"):
            self.assertIn(needed, overlay)
        self.assertNotIn("MainScreen(\\n", overlay)
        self.assertIn('versionName = "0.3.0-beta.1"', overlay)


if __name__ == "__main__":
    unittest.main()

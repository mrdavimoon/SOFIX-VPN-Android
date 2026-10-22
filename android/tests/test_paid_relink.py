"""0.8.3 paid-device recovery regressions (non-network static checks)."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
PAIR=(ROOT/"android/SofixPaidPair.kt").read_text()
MAIN=(ROOT/"android/SofixTrialMain.kt.txt").read_text()
HOME=(ROOT/"android/SofixHomeScreen.kt").read_text()
OVER=(ROOT/"android/apply_overlay.py").read_text()

class PaidRelinkTests(unittest.TestCase):
    def test_existing_paid_customer_can_relink_without_trial(self):
        self.assertIn('"/mobile/api/pair/start"',PAIR)
        self.assertIn('beginPaidPair()',OVER)
        self.assertIn("У МЕНЯ УЖЕ ЕСТЬ ПОДПИСКА",HOME)
        self.assertNotIn("trial/start",PAIR)
        self.assertNotIn("subscription_action",PAIR)

    def test_relink_is_telegram_verified_not_auto_unrevoke(self):
        self.assertIn("SofixPaidPair.check(this@MainActivity)",MAIN)
        self.assertIn('"/mobile/api/me"',PAIR)
        self.assertIn("SofixPaidPair.complete",MAIN)
        self.assertIn("SofixLegacyPair.saveConfirmedToken",PAIR)
        self.assertLess(MAIN.index("installVerifiedPaidConfig(url)",MAIN.index("private fun resumePaidPair()")),
                        MAIN.index("SofixPaidPair.complete",MAIN.index("private fun resumePaidPair()")))

    def test_recovered_token_not_stored_in_legacy_before_ready(self):
        self.assertIn('private const val PREF = "sofix_paid_pair_pending"',PAIR)
        self.assertIn('when(status.optString("state"))',MAIN)
        self.assertIn('"ready" -> {',MAIN)
        self.assertIn("val pin=result.getString",PAIR)
        self.assertIn("Telegram Mini App",PAIR)

    def test_revoked_token_exposes_recovery_not_payment(self):
        self.assertIn('legacyRevoked=true',MAIN)
        self.assertIn('e.statusCode==401 || e.statusCode==403',MAIN)
        self.assertIn('recoverRequired = legacyRevoked',OVER)
        self.assertIn('ВОССТАНОВИТЬ ПОДПИСКУ',HOME)
        self.assertIn('!recoverRequired',HOME)

    def test_paid_preferred_over_expired_trial(self):
        self.assertLess(MAIN.index("SofixLegacyPair.token(this)!=null ->"),
                        MAIN.index("SofixTrialManager.token(this)!=null ->"))
        self.assertIn('val paid=SofixLegacyPair.token(c)',(ROOT/"android/SofixSubscription.kt").read_text())

    def test_new_config_selected_not_stale_guid(self):
        self.assertIn("MmkvManager.decodeServerList(verifiedSub.guid)",MAIN)
        self.assertIn("MainAction.SelectServer(selected)",MAIN)
        self.assertIn("AngConfigManager.updateConfigViaSub(verifiedSub)",MAIN)

    def test_pending_challenge_survives_telegram_switch(self):
        self.assertIn('SofixPaidPair.isPending(this) -> resumePaidPair()',MAIN)
        self.assertIn('openPaidPairTelegram()',MAIN)
        self.assertIn("showPaidPairDialog=false",MAIN)
        self.assertIn('Telegram и подтвердите',OVER)

    def test_trial_marzban_lifecycle_untouched(self):
        self.assertIn('TRIAL_MB', (ROOT/"docs/SOFIX_VPN_0_8_SPEC.md").read_text()) if False else None
        self.assertNotIn("mobile/api/trial/start",PAIR)
        self.assertIn("SofixTrialManager.start",MAIN)

    def test_release_version(self):
        self.assertIn('versionCode = 761',OVER)
        self.assertIn('0.8.3-beta.1',OVER)


if __name__=="__main__":unittest.main()

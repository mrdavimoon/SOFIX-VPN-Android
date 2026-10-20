"""Paid Android devices from earlier SOFIX versions remain recognized."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

class LegacyPaidDeviceRecovery(unittest.TestCase):
    def test_legacy_pair_token_not_hidden_by_missing_flag(self):
        code=(ROOT/'android/SofixLegacyPair.kt').read_text()
        self.assertIn('getString("token", null)',code)
        self.assertIn('fun paired(context: Context): Boolean = token(context) != null',code)
        self.assertNotIn('return p.getBoolean("pair_complete",false) &&',code)
    def test_existing_paid_device_is_verified_with_api(self):
        flow=(ROOT/'android/SofixTrialMain.kt.txt').read_text()
        self.assertIn('refreshLegacyDeviceState()',flow)
        self.assertIn('SofixTrialManager.api("GET","/mobile/api/me",token)',flow)
        self.assertIn('when(result.optString("state"))',flow)
        self.assertIn('"ready" -> {',flow)
        self.assertIn('SofixLegacyPair.markConfirmed',flow)
    def test_subscription_recovered_without_new_device_payment(self):
        flow=(ROOT/'android/SofixTrialMain.kt.txt').read_text()
        block=flow[flow.index('private fun refreshLegacyDeviceState()'):flow.index('private fun refreshTrialServerState()')]
        self.assertIn('AngConfigManager.importBatchConfig(url,"",false)',block)
        self.assertIn('MainAction.RefreshGroups',block)
        self.assertIn('MainAction.UpdateSubscriptions',block)
        self.assertIn('MmkvManager.decodeSubscriptions().any',block)
        self.assertIn('it.subscription.url==url',block)
        self.assertIn('if(!installed)',block)
        self.assertNotIn('SofixTrialManager.start',block)
        self.assertNotIn('/mobile/api/pair/start',block)
        self.assertNotIn('beginLink(',block)
    def test_network_failure_doesnt_erase_credentials(self):
        flow=(ROOT/'android/SofixTrialMain.kt.txt').read_text()
        block=flow[flow.index('private fun refreshLegacyDeviceState()'):flow.index('private fun refreshTrialServerState()')]
        self.assertIn('catch(e:Exception)',block)
        self.assertNotIn('remove("token")',block)
        self.assertNotIn('clear()',block)
    def test_home_and_paid_status_share_legacy_token(self):
        flow=(ROOT/'android/apply_overlay.py').read_text()
        sub=(ROOT/'android/SofixSubscription.kt').read_text()
        self.assertIn('SofixLegacyPair.paired(this)',flow)
        self.assertIn('SofixLegacyPair.token(c)',sub)
    def test_version(self):
        s=(ROOT/'android/apply_overlay.py').read_text()
        self.assertIn('versionCode = 760',s)
        self.assertIn('0.8.2-beta.1',s)
        self.assertTrue((ROOT/'release-notes/v0.8.2-beta.1.md').exists())

if __name__=='__main__':
 unittest.main()

"""0.8 regression gates (build-time assertions, not a replacement for phone QA)."""
import re
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

class Version08(unittest.TestCase):
    def test_three_modes(self):
        code=(ROOT/"android/SofixRouting.kt").read_text()
        for value in ('SMART = "smart"','ALL = "all"','SELECTED = "selected"'):
            self.assertIn(value,code)
        for attr in ("fun explain(", "fun mutate(", "fun apply(", "canonical("):
            self.assertIn(attr,code)
    def test_manual_rules_before_auto(self):
        s=(ROOT/"android/SofixRouting.kt").read_text()
        self.assertLess(s.index('for((domain,tag) in manual)'),s.index('SofixRuleUpdates.blocked(c).map'))
        self.assertLess(s.index('SofixRuleUpdates.blocked(c).map'),s.index('geosite:category-ru'))
        self.assertNotIn('geoip:ru',s)
        self.assertIn('geoip:private',s)
    def test_selected_fallback_direct(self):
        s=(ROOT/"android/SofixRouting.kt").read_text()
        self.assertIn('domain=listOf("regexp:.*")',s)
        self.assertIn('ip=listOf("0.0.0.0/0","::/0")',s)
        self.assertIn('expert_confirmed',s)
    def test_routing_connected_to_xray(self):
        s=(ROOT/"android/apply_overlay.py").read_text()
        self.assertIn('SofixRouting.apply(configContext.context,v2rayConfig)',s)
        self.assertIn('CoreConfigManager.kt',s)
    def test_signed_separate_rules(self):
        s=(ROOT/"android/SofixRuleUpdates.kt").read_text()
        for v in ("Ed25519","initVerify","verifier.verify","blocked_domains","ru_domains","payload"):
            self.assertIn(v,s)
        self.assertNotIn("PRIVATE KEY",s)
        self.assertTrue((ROOT/"rules/manifest.json").is_file())
    def test_simple_routing_settings(self):
        s=(ROOT/"android/SofixRoutingActivity.kt").read_text()
        for v in ("Всегда через VPN","Всегда напрямую","Умный","Только выбранные","Показать маршрут"):
            self.assertIn(v,s)
        p=(ROOT/"android/SofixSimpleSettingsActivity.kt").read_text()
        self.assertIn("Раздельное туннелирование",p)
    def test_health_and_subscription(self):
        health=(ROOT/"android/SofixDiagnosticsActivity.kt").read_text()
        home=(ROOT/"android/SofixHomeScreen.kt").read_text()
        sub=(ROOT/"android/SofixSubscription.kt").read_text()
        self.assertIn("WAITING_NETWORK",health)
        self.assertIn("DEGRADED",health)
        self.assertIn("networkState",home)
        self.assertIn("/mobile/api/me",sub)
        self.assertIn("/mobile/api/trial/status",sub)
    def test_demo_no_side_effects(self):
        demo=(ROOT/"android/SofixDemoActivity.kt").read_text()
        self.assertIn("ДЕМОНСТРАЦИЯ",demo)
        for bad in ("trial/start","startService(", "SofixTrialManager.api", "SofixRouting.setMode"):
            self.assertNotIn(bad,demo)
        self.assertIn("versionTaps>=7",(ROOT/"android/SofixSimpleSettingsActivity.kt").read_text())
    def test_08_release_note_not_yet_release(self):
        p=ROOT/"release-notes/v0.8.2-beta.1.md"
        self.assertGreater(p.stat().st_size,1000)
        self.assertIn('versionCode = 760',(ROOT/"android/apply_overlay.py").read_text())

if __name__=="__main__":
    unittest.main()

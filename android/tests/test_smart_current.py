"""SMART Moscow baseline: subscription refresh, safe TCP default, signed rules."""
import base64
import json
import re
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
MAIN=(ROOT/'android/SofixTrialMain.kt.txt').read_text()
RULES=(ROOT/'android/SofixRuleUpdates.kt').read_text()

class SmartCurrentTests(unittest.TestCase):
    def test_paid_device_subscription_refresh_preserves_manual_choice(self):
        self.assertIn('AngConfigManager.updateConfigViaSub(verifiedSub)',MAIN)
        self.assertIn('mainViewModel.uiState.value.selectedGuid?.takeIf { it in candidates }',MAIN)
        self.assertIn('MmkvManager.decodeServerList(verifiedSub.guid)',MAIN)

    def test_prefer_supported_moscow_tcp_only(self):
        self.assertIn('title.contains("Москва SMART",ignoreCase=true)',MAIN)
        self.assertIn('title.contains("TCP",ignoreCase=true)',MAIN)
        self.assertIn('profile?.network.equals("tcp",ignoreCase=true)',MAIN)
        self.assertIn('?: candidates.firstOrNull()',MAIN)

    def test_signed_v2_routing_includes_priority_services(self):
        d=json.loads((ROOT/'rules/manifest.json').read_text())
        p=json.loads(d['payload'])
        self.assertGreaterEqual(p['version'],2)
        self.assertEqual(len(base64.b64decode(d['signature'])),64)
        for domain in ('youtube.com','instagram.com','telegram.org','t.me',
                       'whatsapp.com','tiktok.com','discord.com','claude.ai'):
            self.assertIn(domain,p['blocked_domains'])
        self.assertIn('verifier.verify(sig)',RULES)

if __name__=='__main__':unittest.main()

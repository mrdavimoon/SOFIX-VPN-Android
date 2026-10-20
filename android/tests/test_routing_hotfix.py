"""0.8 routing hotfix guards: default routing must not silently disable SOFIX."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
R=(ROOT/"android/SofixRouting.kt").read_text()
UI=(ROOT/"android/SofixRoutingActivity.kt").read_text()
OV=(ROOT/"android/apply_overlay.py").read_text()

class DefaultRoutingHotfix(unittest.TestCase):
    def test_factory_v2rayng_white_rules_dont_disable_sofix(self):
        self.assertIn('isUntouchedFactoryPreset', R)
        self.assertIn('c.assets.open("custom_routing_white")',R)
        self.assertIn('if(experts.isEmpty() || isUntouchedFactoryPreset(c,experts))return true',R)
        self.assertNotIn('if(expert.isNotEmpty()',R)
    def test_factory_rules_are_not_compiled_ahead_of_sofix(self):
        self.assertIn('!com.v2ray.ang.ui.sofix.SofixRouting.managedActive(configContext.context)',OV)
        self.assertIn('SofixRouting.apply(configContext.context,v2rayConfig)',OV)
        self.assertIn('core_source.replace(legacy,managed)',OV)
    def test_dns_port_53_preserved_and_lan_direct(self):
        self.assertIn('it.outboundTag=="dns-out" && it.port=="53"',R)
        self.assertIn('config.routing.rules.addAll(dnsCount,rules)',R)
        self.assertIn('geoip:private',R)
        self.assertIn('geosite:private',R)
    def test_manual_domain_2ip_io_emits_direct_xray_rule(self):
        self.assertIn('full:$domain',R)
        self.assertIn('domain:$domain',R)
        self.assertIn('direct.map{it to AppConfig.TAG_DIRECT}',R)
        self.assertIn('for((domain,tag) in manual)',R)
        self.assertIn('always_direct',R)
    def test_custom_expert_rule_conflicts_shown_not_silent(self):
        self.assertIn('Правила SOFIX пока не применяются',UI)
        self.assertIn('enableManagedWithLegacyBackup',UI)
        self.assertIn('restoreLegacyRules',UI)
        self.assertIn('expert_backup',R)
        self.assertIn('expert_confirmed',R)
    def test_existing_modes_still_available(self):
        for mode in ('"smart"','"all"','"selected"'):
            self.assertIn(mode,R)
        self.assertIn('geosite:category-ru',R)
        self.assertIn('blocked(c)',R)

if __name__=="__main__":unittest.main()

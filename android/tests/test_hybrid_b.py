"""Hybrid B parity, signed data, mode defaults, and DNS safety invariants."""
import base64
import json
import re
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
ROUTING=(ROOT/"android/SofixRouting.kt").read_text()
RULES=(ROOT/"android/SofixRuleUpdates.kt").read_text()
KOTLIN=(ROOT/"android/SofixHybridDefaults.kt").read_text()
MAIN=(ROOT/"android/SofixTrialMain.kt.txt").read_text()
PAYLOAD=json.loads(json.loads((ROOT/"rules/manifest.json").read_text())["payload"])
SOURCE=json.loads((ROOT/"rules/hybrid_b_source.json").read_text())

def domain_match(host, rule):
    kind, domain=rule.split(":",1)
    return host==domain if kind=="full" else host==domain or host.endswith("."+domain)

def route(host):
    if any(domain_match(host,x) for x in SOURCE["proxy_rules"]): return "proxy"
    if any(domain_match(host,x) for x in SOURCE["direct_rules"]): return "direct"
    return "direct"

class HybridBTests(unittest.TestCase):
    def test_signed_manifest_is_same_policy_as_happ_snapshot(self):
        self.assertGreaterEqual(PAYLOAD["version"],3)
        self.assertEqual(SOURCE["global_proxy"],False)
        self.assertEqual(SOURCE["domain_strategy"],"IPIfNonMatch")
        for key in ("proxy_rules","direct_rules","proxy_ips","direct_ips"):
            self.assertEqual(PAYLOAD[key],SOURCE[key])
            self.assertEqual(len(PAYLOAD[key]),len(set(PAYLOAD[key])))
            for value in PAYLOAD[key]:
                self.assertIn(json.dumps(value,ensure_ascii=False),KOTLIN)
        self.assertEqual(len(base64.b64decode(json.loads((ROOT/"rules/manifest.json").read_text())["signature"])),64)

    def test_important_ru_services_use_phone_ip(self):
        for host in ("sberbank.ru","online.sberbank.ru","gosuslugi.ru",
                     "esia.gosuslugi.ru","yandex.ru","ya.ru","vk.com",
                     "ozon.ru","wildberries.ru","whoosh.bike","tbank.ru",
                     "random.ru","example.com"):
            self.assertEqual(route(host),"direct",host)

    def test_restricted_sites_use_proxy(self):
        for host in ("youtube.com","r3.googlevideo.com","telegram.org","t.me",
                     "instagram.com","cdninstagram.com","whatsapp.com",
                     "chatgpt.com","gemini.google.com","openai.com",
                     "spotify.com","netflix.com"):
            self.assertEqual(route(host),"proxy",host)

    def test_route_application_has_cidr_fallback_and_sniffing(self):
        self.assertIn("SofixRuleUpdates.proxyIps(c)",ROUTING)
        self.assertIn("SofixRuleUpdates.directIps(c)",ROUTING)
        self.assertIn('domainStrategy=if(mode(c)==SMART)"IPIfNonMatch"',ROUTING)
        self.assertIn('network="tcp,udp",outboundTag=AppConfig.TAG_DIRECT',ROUTING)
        self.assertIn("it.sniffing?.routeOnly=false",ROUTING)
        self.assertLess(ROUTING.index('add(AppConfig.TAG_PROXY,SofixRuleUpdates.proxyRules(c))'),
                        ROUTING.index('addIp(AppConfig.TAG_PROXY,SofixRuleUpdates.proxyIps(c))'))
        self.assertLess(ROUTING.index('config.routing.rules.addAll(dnsCount,rules)'),
                        ROUTING.index('network="tcp,udp"'))
        self.assertIn('it.outboundTag=="dns-out" && it.port=="53"',ROUTING)

    def test_routing_modes_and_manual_overrides_preserved(self):
        self.assertIn("for((domain,tag) in manual)",ROUTING)
        self.assertIn('if(mode(c)==ALL)return "proxy"',ROUTING)
        self.assertIn('if(mode(c)==SELECTED)return "direct"',ROUTING)
        self.assertIn('mode(c)==SMART || mode(c)==SELECTED',ROUTING)
        self.assertIn('expert_confirmed',ROUTING)
        self.assertIn('SofixRouting.canonical(host)==host',RULES)

    def test_current_moscow_only_and_persistent_entitlement(self):
        self.assertIn('title.contains("Москва-1",ignoreCase=true)',MAIN)
        self.assertIn('title.contains("Москва-2",ignoreCase=true)',MAIN)
        self.assertIn("AngConfigManager.updateConfigViaSub(verifiedSub)",MAIN)
        self.assertIn("selectedGuid?.takeIf { it in candidates }",MAIN)
        self.assertIn('versionCode = 764',(ROOT/"android/apply_overlay.py").read_text())

if __name__=="__main__":unittest.main()

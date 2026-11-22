#!/usr/bin/env python3
"""Export approved Happ Hybrid B routing into Android fallback and Ed25519 manifest.

No Telegram tokens, subscribers, signing material or private rollout settings
are copied. Signing key is read locally but never emitted.
"""
import argparse
import base64
import ipaddress
import json
import re
import time
from pathlib import Path
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

parser = argparse.ArgumentParser()
parser.add_argument("--snapshot", required=True)
parser.add_argument("--signing-key", required=True)
parser.add_argument("--repo", required=True)
parser.add_argument("--version", type=int, default=3)
args = parser.parse_args()
root = Path(args.repo).resolve()
snap = json.loads(Path(args.snapshot).read_text())
policy = snap["client_routing"]
header = policy["hybrid_header"]
assert header.startswith("happ://routing/onadd/")
profile = json.loads(base64.b64decode(header.rsplit("/", 1)[1], validate=True))
assert profile["GlobalProxy"] is False
assert profile["RouteOrder"] == "block-direct-proxy"
assert profile["DomainStrategy"] == "IPIfNonMatch"
assert profile["FakeDNS"] is False
assert profile["BlockSites"] == [] and profile["BlockIp"] == []
for key in ("ProxySites", "DirectSites"):
    rules = profile[key]
    assert len(set(rules)) == len(rules)
    for rule in rules:
        tag, host = rule.split(":", 1)
        assert tag in ("domain", "full")
        assert re.fullmatch(r"[a-z0-9][a-z0-9.-]{2,252}", host)
for key in ("ProxyIp", "DirectIp"):
    assert len(set(profile[key])) == len(profile[key])
    for cidr in profile[key]:
        ipaddress.ip_network(cidr, strict=False)
assert len(profile["ProxySites"]) >= 80 and len(profile["ProxyIp"]) >= 20
assert not(set(profile["ProxySites"]) & set(profile["DirectSites"]))

def kotlin_list(name, items):
    return "    val " + name + " = listOf(\n" + "".join(
        "        " + json.dumps(x, ensure_ascii=False) + ",\n" for x in items
    ) + "    )\n"

source = """package com.v2ray.ang.ui.sofix

/** Offline fallback generated from approved SOFIX Happ Hybrid B policy.
 * Online changes use signed /rules/manifest.json; never embed credentials.
 */
object SofixHybridDefaults {
"""
for key, field in (("proxyRules", "ProxySites"), ("directRules", "DirectSites"),
                   ("proxyIps", "ProxyIp"), ("directIps", "DirectIp")):
    source += kotlin_list(key, profile[field])
source += "}\n"
(root/"android/SofixHybridDefaults.kt").write_text(source)

# Earlier Android versions still use the legacy domain-only lists.
proxy_domains = sorted({r.split(":", 1)[1] for r in profile["ProxySites"]})
direct_domains = sorted({r.split(":", 1)[1] for r in profile["DirectSites"]})
now = int(time.time())
payload = {
    "format": 1, "version": args.version,
    "generated_at": now, "expires_at": now + 90 * 86400,
    "blocked_domains": proxy_domains, "ru_domains": direct_domains,
    "proxy_rules": profile["ProxySites"], "direct_rules": profile["DirectSites"],
    "proxy_ips": profile["ProxyIp"], "direct_ips": profile["DirectIp"],
}
key = serialization.load_pem_private_key(Path(args.signing_key).read_bytes(), password=None)
assert isinstance(key, Ed25519PrivateKey)
original = json.loads((root/"rules/manifest.json").read_text())
oldpayload = json.loads(original["payload"])
assert args.version > int(oldpayload["version"])
# An exact serialization is what Ed25519 authenticates, not normalized JSON.
serialized = json.dumps(payload, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
sig = base64.b64encode(key.sign(serialized.encode("utf-8"))).decode("ascii")
(root/"rules/manifest.json").write_text(
    json.dumps({"payload": serialized, "signature": sig}, ensure_ascii=False, indent=2) + "\n"
)
(root/"rules/hybrid_b_source.json").write_text(
    json.dumps({
        "profile_name": profile["Name"],
        "global_proxy": profile["GlobalProxy"],
        "domain_strategy": profile["DomainStrategy"],
        "proxy_rules": profile["ProxySites"], "direct_rules": profile["DirectSites"],
        "proxy_ips": profile["ProxyIp"], "direct_ips": profile["DirectIp"],
    }, ensure_ascii=False, indent=2) + "\n"
)
print("ANDROID_HYBRID_EXPORT_OK", "signed_version", args.version,
      "proxy_domains", len(profile["ProxySites"]),
      "proxy_cidrs", len(profile["ProxyIp"]),
      "direct_domains", len(profile["DirectSites"]),
      "direct_cidrs", len(profile["DirectIp"]))

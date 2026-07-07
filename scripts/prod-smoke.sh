#!/usr/bin/env bash
# Production hardening acceptance test.
# Usage: scripts/prod-smoke.sh <domain> [--insecure]
#   --insecure  use curl -k (local rehearsal with Caddy 'tls internal')
set -uo pipefail

DOMAIN="${1:?usage: prod-smoke.sh <domain> [--insecure]}"
CURL_TLS=""
[ "${2:-}" = "--insecure" ] && CURL_TLS="-k"

fail=0
ok()   { echo "  ✓ $*"; }
bad()  { echo "  ✗ $*"; fail=1; }

echo "== 1. Isolation: debug ports must NOT answer from the host =="
for port in 8082 8091 8084 8089 8086 8088 8087 5434 9200 8090 3000 6380; do
  if timeout 2 bash -c "</dev/tcp/${DOMAIN}/${port}" 2>/dev/null; then
    bad "port ${port} is reachable (should be closed)"
  else
    ok "port ${port} closed"
  fi
done

echo "== 2. HTTP redirects to HTTPS =="
code=$(curl -s -o /dev/null -w '%{http_code}' "http://${DOMAIN}/" || echo 000)
case "$code" in 301|302|308) ok "HTTP → HTTPS ($code)";; *) bad "expected redirect, got $code";; esac

echo "== 3. HTTPS serves the SPA =="
code=$(curl $CURL_TLS -s -o /dev/null -w '%{http_code}' "https://${DOMAIN}/")
[ "$code" = "200" ] && ok "SPA 200" || bad "SPA got $code"

echo "== 4. Actuator blocked at the proxy =="
code=$(curl $CURL_TLS -s -o /dev/null -w '%{http_code}' "https://${DOMAIN}/actuator/health")
[ "$code" = "403" ] && ok "actuator 403" || bad "actuator got $code (expected 403)"

echo "== 5. End-to-end: public catalog through Caddy =="
code=$(curl $CURL_TLS -s -o /dev/null -w '%{http_code}' "https://${DOMAIN}/api/events?page=0&size=1")
[ "$code" = "200" ] && ok "catalog 200" || bad "catalog got $code"

echo "== 6. Security headers present =="
hdrs=$(curl $CURL_TLS -sI "https://${DOMAIN}/")
echo "$hdrs" | grep -qi "x-frame-options: DENY" && ok "X-Frame-Options" || bad "missing X-Frame-Options"
echo "$hdrs" | grep -qi "x-content-type-options: nosniff" && ok "X-Content-Type-Options" || bad "missing X-Content-Type-Options"

echo
[ "$fail" = 0 ] && echo "ALL CHECKS PASSED" || { echo "SOME CHECKS FAILED"; exit 1; }

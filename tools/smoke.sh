#!/usr/bin/env bash
# Smoke checks for CI: three-service topology, login, two reports and metrics.
set -euo pipefail

BASE="${BASE_URL:-http://localhost:8083}"
CATALOG="${CATALOG_URL:-http://localhost:8081}"
PRICE="${PRICE_URL:-http://localhost:8082}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
COOKIE="$TMP/cookies.txt"

status="$(curl -sS -o "$TMP/login.html" -w '%{http_code}' -c "$COOKIE" \
  -d 'email=demo@example.com&password=demopass123' "$BASE/login")"
case "$status" in
  200|302) ;;
  *) echo "login failed: HTTP $status" >&2; exit 1 ;;
esac

check_page() {
  local name="$1"
  local url="$2"
  local code
  code="$(curl -sS -o "$TMP/$name.html" -w '%{http_code}' -b "$COOKIE" "$url")"
  if [ "$code" != "200" ]; then
    echo "$name failed: HTTP $code" >&2
    exit 1
  fi
  if grep -q 'Whitelabel Error' "$TMP/$name.html"; then
    echo "$name rendered an error page" >&2
    exit 1
  fi
  echo "$name OK"
}

check_page build-pc "$BASE/cheaper?mode=build&category=pc&purpose=game&priority=performance&region=Moscow&budget=120000&wishes=RGB"
check_page build-laptop "$BASE/cheaper?mode=build&category=laptop&purpose=study&priority=battery&screen=13_14&os=any&region=Moscow&budget=90000&requirements=RAM%2016"

curl -fsS "$BASE/actuator/health" > "$TMP/health.json"
grep -q '"status":"UP"' "$TMP/health.json"
echo "health OK"

# gateway -> catalog -> price: один запрос доказывает, что все три сервиса общаются.
curl -fsS -b "$COOKIE" "$BASE/api/topology" > "$TMP/topology.json"
grep -q '"service":"gateway"' "$TMP/topology.json"
grep -q '"service":"catalog"' "$TMP/topology.json"
grep -q '"service":"price"' "$TMP/topology.json"
echo "topology OK"

curl -fsS "$CATALOG/actuator/metrics" > "$TMP/catalog-metrics.json"
grep -q 'pt.cache.access' "$TMP/catalog-metrics.json"
curl -fsS "$PRICE/actuator/metrics" > "$TMP/price-metrics.json"
grep -q 'pt.store.requests' "$TMP/price-metrics.json"
echo "metrics OK"

echo "Smoke checks passed"

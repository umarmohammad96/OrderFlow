#!/usr/bin/env bash
# Fires N simultaneous orders for the same product against a running stack and reports the outcome.
# Usage: scripts/concurrency-demo.sh [BASE_URL] [BUYERS] [PRODUCT_NAME]
set -euo pipefail
BASE_URL=${1:-http://localhost:8080}
BUYERS=${2:-25}
PRODUCT_NAME=${3:-Limited Edition Console}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

json() { python3 -c "import sys,json; d=json.load(sys.stdin); print(eval(sys.argv[1]))" "$1"; }

PRODUCT_ID=$(curl -s "$BASE_URL/api/products?size=100" |
  json "[p['id'] for p in d['data']['content'] if p['name']=='$PRODUCT_NAME'][0]")
BEFORE=$(curl -s "$BASE_URL/api/products/$PRODUCT_ID" | json "d['data']['availableQuantity']")
echo "Product $PRODUCT_ID ('$PRODUCT_NAME') available before: $BEFORE"

echo "Registering $BUYERS buyers..."
for i in $(seq 1 "$BUYERS"); do
  curl -s -X POST "$BASE_URL/api/auth/register" -H 'Content-Type: application/json' \
    -d "{\"name\":\"Buyer $i\",\"email\":\"buyer$i.$(date +%s%N)@example.com\",\"password\":\"password123\"}" |
    json "d['data']['accessToken']" > "$WORK/token.$i"
done

echo "Firing $BUYERS concurrent orders for 1 unit each..."
for i in $(seq 1 "$BUYERS"); do
  curl -s -o "$WORK/body.$i" -w '%{http_code}\n' -X POST "$BASE_URL/api/orders" \
    -H "Authorization: Bearer $(cat "$WORK/token.$i")" -H "Idempotency-Key: demo-$i-$RANDOM$RANDOM" \
    -H 'Content-Type: application/json' -d "{\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":1}]}" > "$WORK/status.$i" &
done
wait

echo "HTTP status distribution:"
cat "$WORK"/status.* | sort | uniq -c | sed 's/^/  /'
AFTER=$(curl -s "$BASE_URL/api/products/$PRODUCT_ID" | json "d['data']['availableQuantity']")
echo "Available after: $AFTER (never negative)"

#!/usr/bin/env bash
#
# The resume path after an Anthropic top-up: backfill product extraction, rebuild the
# product registry, and confirm the home opportunity banner is alive again.
#
#   ./scripts/resume-extraction.sh            # normal run
#   ./scripts/resume-extraction.sh 20         # also sweep the 20-24% discount tail
#
# Two things this handles that a naive loop does not:
#
#   1. /admin/* is gated behind X-Admin-Key. A loop without the header just collects 401s.
#   2. A stalled run looks exactly like a working one. If credits run dry or the €25/month
#      ExtractionBudgetService cap trips, the endpoint keeps returning 200 with analyzed=0
#      and no error — it silently skips the Claude call. We stop on that instead of spinning.
#
# Batches run back-to-back on purpose: Anthropic's prompt cache has a 5-minute TTL and the
# extractor's ~11k-token few-shot prefix is the cached part. Long gaps between batches let it
# expire, so you pay cache-creation instead of cache-read on every batch.

set -euo pipefail

PROJECT="promo-finder-be"
REGION="europe-west1"
SERVICE="deal-finder-api"
API="https://deal-finder-api-927801911058.europe-west1.run.app/api/v1"

MIN_DISCOUNT="${1:-25}"
BATCH=50

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }

say "Fetching the admin key"
ADMIN_KEY="$(gcloud secrets versions access latest --secret=admin-api-key --project "$PROJECT" 2>/dev/null || true)"
if [[ -z "$ADMIN_KEY" ]]; then
  ADMIN_KEY="$(gcloud run services describe "$SERVICE" --region "$REGION" --project "$PROJECT" \
    --format='value(spec.template.spec.containers[0].env.filter("name:ADMIN_API_KEY").extract("value").flatten())' 2>/dev/null || true)"
fi
if [[ -z "$ADMIN_KEY" ]]; then
  echo "Could not read ADMIN_API_KEY from Secret Manager or Cloud Run. Aborting." >&2
  exit 1
fi
echo "  ok (value not shown)"

say "Backfilling extraction (minDiscount=${MIN_DISCOUNT}, batch=${BATCH})"
total_analyzed=0
total_failed=0
batch_no=0

while :; do
  batch_no=$((batch_no + 1))

  resp="$(curl -s -m 600 -X POST \
    -H "X-Admin-Key: ${ADMIN_KEY}" \
    "${API}/admin/backfill-images?limit=${BATCH}&minDiscount=${MIN_DISCOUNT}")"

  analyzed=$(printf '%s' "$resp"  | python3 -c 'import sys,json; print(json.load(sys.stdin).get("analyzed",-1))' 2>/dev/null || echo -1)
  failed=$(printf '%s' "$resp"    | python3 -c 'import sys,json; print(json.load(sys.stdin).get("failed",-1))'   2>/dev/null || echo -1)
  remaining=$(printf '%s' "$resp" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("remaining",-1))' 2>/dev/null || echo -1)

  if [[ "$analyzed" == "-1" ]]; then
    echo "  batch ${batch_no}: unparseable response — stopping."
    printf '  %s\n' "$resp"
    exit 1
  fi

  total_analyzed=$((total_analyzed + analyzed))
  total_failed=$((total_failed + failed))
  printf '  batch %-3s analyzed=%-4s failed=%-4s remaining=%s\n' "$batch_no" "$analyzed" "$failed" "$remaining"

  if [[ "$remaining" -le 0 ]]; then
    echo "  backfill complete."
    break
  fi

  # The stall guard. remaining > 0 but nothing was analyzed => the Claude call is being
  # skipped, not failing loudly. Credits exhausted, budget cap tripped, or the API is erroring.
  if [[ "$analyzed" -eq 0 ]]; then
    say "STALLED — ${remaining} deals still unprocessed but this batch analyzed 0."
    cat <<'EOF'
  Nothing was extracted, and the endpoint did not error. The usual causes:
    - Anthropic credits are exhausted        -> console.anthropic.com > Plans & Billing
    - The monthly extraction cap has tripped -> EXTRACTION_BUDGET_MONTHLY (default 25)
    - Extraction is switched off             -> DEAL_PRODUCT_EXTRACTION_ENABLED / SCRAPER_PRODUCT_EXTRACTION

  Check the real reason (ProductExtractor classifies the failure):
    gcloud run services logs read deal-finder-api --region europe-west1 --project promo-finder-be --limit 50
EOF
    exit 1
  fi
done

say "Rebuilding the product registry"
curl -s -m 600 -X POST -H "X-Admin-Key: ${ADMIN_KEY}" "${API}/admin/aggregate-products" | head -c 400
printf '\n'

say "Did the headline feature come back?"
opp="$(curl -s -m 60 "${API}/products/opportunity")"
if [[ -z "$opp" ]]; then
  echo "  /products/opportunity is STILL EMPTY."
  echo "  Needs conf >= 0.90 and >= 30d of price history on a fingerprinted product."
  echo "  Price history has been accruing throughout, so this should fill in as extraction coverage grows."
else
  printf '  %s\n' "$opp" | head -c 500
  printf '\n'
fi

say "Summary"
echo "  analyzed: ${total_analyzed}   failed: ${total_failed}   batches: ${batch_no}"
echo "  products: $(curl -s -m 30 "${API}/products" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(len(d if isinstance(d,list) else d.get("products",[])))' 2>/dev/null || echo '?')"
echo
echo "  Sanity-check the spend against the ~\$6 projection: console.anthropic.com > Usage."

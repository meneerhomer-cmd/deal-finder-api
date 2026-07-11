#!/usr/bin/env bash
#
# Move ANTHROPIC_API_KEY, QUARKUS_DATASOURCE_PASSWORD and ADMIN_API_KEY off Cloud Run
# plaintext env vars and into Secret Manager.
#
# Run this YOURSELF (it prompts for the rotated Anthropic key; nothing is echoed).
#
#   ./scripts/migrate-secrets.sh
#
# Rotate the Anthropic key in the console FIRST. The old key was exposed, and an
# exposed key on a funded account is a spending risk — rotate before you top up.

set -euo pipefail

PROJECT="promo-finder-be"
REGION="europe-west1"
SERVICE="deal-finder-api"
RUNTIME_SA="927801911058-compute@developer.gserviceaccount.com"

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }

upsert_secret() {
  local name="$1"
  if gcloud secrets describe "$name" --project "$PROJECT" >/dev/null 2>&1; then
    gcloud secrets versions add "$name" --project "$PROJECT" --data-file=- >/dev/null
    echo "  updated secret: $name (new version)"
  else
    gcloud secrets create "$name" --project "$PROJECT" --replication-policy=automatic --data-file=- >/dev/null
    echo "  created secret: $name"
  fi
}

current_env() {
  # Reads one env var's value off the running service WITHOUT printing it.
  gcloud run services describe "$SERVICE" --region "$REGION" --project "$PROJECT" \
    --format="value(spec.template.spec.containers[0].env.filter(\"name:$1\").extract(\"value\").flatten())"
}

# Carry a plaintext env var into Secret Manager, but ONLY if it is still there.
# On a second run the env var is already gone, and writing its empty value would
# clobber a perfectly good secret with a blank version.
carry_env_to_secret() {
  local env_name="$1" secret_name="$2" value
  value="$(current_env "$env_name" | tr -d '\n')"
  if [[ -z "$value" ]]; then
    if gcloud secrets describe "$secret_name" --project "$PROJECT" >/dev/null 2>&1; then
      echo "  skipped $secret_name — no plaintext ${env_name} on the service (already migrated)"
    else
      echo "  ERROR: ${env_name} is not set on the service and no '${secret_name}' secret exists." >&2
      exit 1
    fi
    return
  fi
  printf '%s' "$value" | upsert_secret "$secret_name"
}

say "1/4  Anthropic API key (rotated)"
printf '     Paste the NEW Anthropic key (input hidden), then press enter: '
read -rs ANTHROPIC_KEY
printf '\n'
if [[ -z "${ANTHROPIC_KEY}" ]]; then
  echo "     No key entered — aborting." >&2
  exit 1
fi
printf '%s' "$ANTHROPIC_KEY" | upsert_secret "anthropic-api-key"
unset ANTHROPIC_KEY

say "2/4  Carrying the existing DB password and admin key across (values never printed)"
carry_env_to_secret QUARKUS_DATASOURCE_PASSWORD db-password
carry_env_to_secret ADMIN_API_KEY               admin-api-key

say "3/4  Granting the Cloud Run service account read access"
for s in anthropic-api-key db-password admin-api-key; do
  gcloud secrets add-iam-policy-binding "$s" \
    --project "$PROJECT" \
    --member "serviceAccount:${RUNTIME_SA}" \
    --role roles/secretmanager.secretAccessor >/dev/null
  echo "  granted secretAccessor on: $s"
done

say "4/4  Repointing Cloud Run at the secrets and dropping the plaintext env vars"
gcloud run services update "$SERVICE" \
  --region "$REGION" --project "$PROJECT" \
  --remove-env-vars ANTHROPIC_API_KEY,QUARKUS_DATASOURCE_PASSWORD,ADMIN_API_KEY \
  --set-secrets ANTHROPIC_API_KEY=anthropic-api-key:latest,QUARKUS_DATASOURCE_PASSWORD=db-password:latest,ADMIN_API_KEY=admin-api-key:latest \
  --quiet

say "Verifying"
echo "  plaintext env vars still on the service (want: none of the three):"
gcloud run services describe "$SERVICE" --region "$REGION" --project "$PROJECT" \
  --format='value(spec.template.spec.containers[0].env)' \
  | tr ';' '\n' | grep -oE "'name': '(ANTHROPIC_API_KEY|QUARKUS_DATASOURCE_PASSWORD|ADMIN_API_KEY)'" || echo "    none — clean"

code=$(curl -s -o /dev/null -w '%{http_code}' \
  "https://deal-finder-api-927801911058.europe-west1.run.app/api/v1/retailers")
echo "  /api/v1/retailers -> HTTP $code (want 200)"

say "Done. The old plaintext key is no longer on the service."
echo "Revoke the OLD Anthropic key in the console if you haven't already."

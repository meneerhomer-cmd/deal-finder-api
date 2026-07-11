#!/usr/bin/env bash
#
# Move ANTHROPIC_API_KEY, QUARKUS_DATASOURCE_PASSWORD and ADMIN_API_KEY off Cloud Run
# plaintext env vars and into Secret Manager.
#
#   ./scripts/migrate-secrets.sh            # carry the existing values across
#   ./scripts/migrate-secrets.sh --rotate   # paste a new Anthropic key instead (hidden input)
#
# Values are read straight off the running service and piped into Secret Manager —
# never printed, never passed on a command line.

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

say "1/4  Anthropic API key"
if [[ "${1:-}" == "--rotate" ]]; then
  printf '     Paste the NEW Anthropic key (input hidden), then press enter: '
  read -rs ANTHROPIC_KEY
  printf '\n'
  if [[ -z "${ANTHROPIC_KEY}" ]]; then
    echo "     No key entered — aborting." >&2
    exit 1
  fi
  printf '%s' "$ANTHROPIC_KEY" | upsert_secret "anthropic-api-key"
  unset ANTHROPIC_KEY
  echo "  stored the rotated key — revoke the OLD one in the console."
else
  carry_env_to_secret ANTHROPIC_API_KEY anthropic-api-key
  echo "  carried the existing key across (pass --rotate to paste a new one instead)"
fi

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
# --set-secrets mounts each secret AS an env var, so the NAME is present either way.
# The only thing that distinguishes migrated from exposed is value (inline) vs
# valueFrom.secretKeyRef — grepping for the name would report success regardless.
gcloud run services describe "$SERVICE" --region "$REGION" --project "$PROJECT" --format=json \
  | python3 -c "
import sys, json
env = json.load(sys.stdin)['spec']['template']['spec']['containers'][0].get('env', [])
watched = {'ANTHROPIC_API_KEY', 'QUARKUS_DATASOURCE_PASSWORD', 'ADMIN_API_KEY'}
bad = 0
for e in env:
    if e.get('name') not in watched:
        continue
    if e.get('value'):
        print(f\"  {e['name']}: PLAINTEXT — still exposed\"); bad += 1
    elif 'valueFrom' in e:
        ref = e['valueFrom'].get('secretKeyRef', {})
        print(f\"  {e['name']}: secretKeyRef -> {ref.get('name')}:{ref.get('key')}\")
sys.exit(1 if bad else 0)
"

code=$(curl -s -o /dev/null -w '%{http_code}' \
  "https://deal-finder-api-927801911058.europe-west1.run.app/api/v1/retailers")
echo "  /api/v1/retailers -> HTTP $code (want 200 — proves the DB password resolves)"

say "Done. None of the three secrets are plaintext env vars on the service any more."

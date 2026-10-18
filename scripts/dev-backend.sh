#!/usr/bin/env bash
# Run the backend locally (without Docker) using the repo's `.env` for configuration.
#
# `.env` is written for docker compose: compose interpolates its values, so a literal
# `$` (e.g. in a bcrypt hash) must be doubled as `$$`. Spring does not read `.env` and
# expects raw values. This script bridges the two:
#   - picks POSTGRES_PORT / OWNER_EMAIL / OWNER_PASSWORD_HASH out of `.env`
#   - un-escapes `$$` back to `$` so Spring gets the real bcrypt hash
#   - points SPRING_DATASOURCE_URL at localhost:${POSTGRES_PORT} (compose publishes
#     Postgres on that port; application.yml's default only matches 5432)
#
# Usage:
#   scripts/dev-backend.sh          # from anywhere; cd's into backend/ itself
#
# Requires either a running Postgres on ${POSTGRES_PORT} or the compose postgres
# service (started automatically if docker is available).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/.env"

if [[ -f "$ENV_FILE" ]]; then
  while IFS='=' read -r key value; do
    key="${key//$'\r'/}"
    value="${value//$'\r'/}"
    value="${value%\"}"
    value="${value#\"}"
    case "$key" in
      POSTGRES_PORT | OWNER_EMAIL | OWNER_PASSWORD_HASH) export "$key=$value" ;;
    esac
  done < "$ENV_FILE"

  if [[ -n "${OWNER_PASSWORD_HASH:-}" && "$OWNER_PASSWORD_HASH" == *'$$'* ]]; then
    OWNER_PASSWORD_HASH="${OWNER_PASSWORD_HASH//\$\$/\$}"
    export OWNER_PASSWORD_HASH
  fi
else
  echo "dev-backend: no .env at $ROOT/.env — owner bootstrap will be skipped; continuing." >&2
fi

POSTGRES_PORT="${POSTGRES_PORT:-5432}"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${POSTGRES_PORT}/tichu_turnier"

if ! (exec 3<>"/dev/tcp/localhost/${POSTGRES_PORT}") 2>/dev/null; then
  echo "dev-backend: nothing listening on localhost:${POSTGRES_PORT}; trying 'docker compose up -d postgres'..." >&2
  (cd "$ROOT" && docker compose up -d postgres) >&2 || {
    echo "dev-backend: could not start postgres. Start a Postgres on localhost:${POSTGRES_PORT} yourself." >&2
    exit 1
  }
fi

cd "$ROOT/backend"
exec ./gradlew bootRun
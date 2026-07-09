#!/bin/sh
# Generates /config.js from environment variables at container start so the
# same image runs in any environment (nginx runs all /docker-entrypoint.d/*.sh).
set -eu

CONFIG_FILE=/usr/share/nginx/html/config.js

{
  echo "window.__FINYO_CONFIG__ = {"
  if [ -n "${KEYCLOAK_URL:-}" ]; then
    echo "  keycloakUrl: \"${KEYCLOAK_URL}\","
  fi
  if [ -n "${KEYCLOAK_CLIENT_ID:-}" ]; then
    echo "  keycloakClientId: \"${KEYCLOAK_CLIENT_ID}\","
  fi
  echo "};"
} > "$CONFIG_FILE"

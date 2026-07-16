#!/bin/sh
# Substitutes __AUTH_ORIGIN__ in the CSP with Keycloak's origin so the same image
# runs in any environment (nginx runs all /docker-entrypoint.d/*.sh, in order —
# this must precede 40-runtime-config.sh only by convention, they are unrelated).
#
# Keycloak is a foreign origin to the app (dev: localhost:8081, prod: the shared
# auth subdomain), so connect-src has to name it explicitly. The fallback mirrors
# AuthProvider.tsx, which assumes the same dev Keycloak when no config is given.
set -eu

CONF=/etc/nginx/conf.d/default.conf
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8081/realms/finyo}"

# scheme://host[:port] — CSP source expressions take an origin, not a path.
AUTH_ORIGIN=$(printf '%s' "$KEYCLOAK_URL" | sed -E 's#^([A-Za-z][A-Za-z0-9+.-]*://[^/]+).*#\1#')

case "$AUTH_ORIGIN" in
  *://*) ;;
  *)
    echo "30-csp.sh: KEYCLOAK_URL='${KEYCLOAK_URL}' has no scheme://host — refusing to write a broken CSP" >&2
    exit 1
    ;;
esac

# '#' delimiter: the origin contains '/'. The URL is operator-supplied config,
# not user input.
sed -i "s#__AUTH_ORIGIN__#${AUTH_ORIGIN}#g" "$CONF"
echo "30-csp.sh: CSP connect-src allows ${AUTH_ORIGIN}"

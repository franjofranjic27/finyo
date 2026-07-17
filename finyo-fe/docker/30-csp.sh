#!/bin/sh
# Substitutes __AUTH_ORIGIN__ in the CSP with Keycloak's origin so the same image
# runs in any environment (nginx runs all /docker-entrypoint.d/*.sh in order).
# The number only slots this in ahead of 40-runtime-config.sh for readability —
# the two are independent and neither depends on the other having run.
#
# Keycloak is a foreign origin to the app (dev: localhost:8081, prod: the shared
# auth subdomain), so connect-src has to name it explicitly. The fallback mirrors
# AuthProvider.tsx, which assumes the same dev Keycloak when no config is given.
set -eu

CONF=/etc/nginx/conf.d/default.conf
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8081/realms/finyo}"

# scheme://host[:port] — CSP source expressions take an origin, not a path.
AUTH_ORIGIN=$(printf '%s' "$KEYCLOAK_URL" | sed -E 's#^([A-Za-z][A-Za-z0-9+.-]*://[^/]+).*#\1#')

# Allowlist, not a structural check: the value is spliced into an nginx string,
# so anything past a plain origin (a stray quote from an .env, a space, a ';')
# would silently weaken or break the CSP rather than fail loudly.
if ! printf '%s' "$AUTH_ORIGIN" | grep -qE '^https?://[A-Za-z0-9._-]+(:[0-9]{1,5})?$'; then
  echo "30-csp.sh: KEYCLOAK_URL='${KEYCLOAK_URL}' is not a plain scheme://host[:port] origin — refusing to write a broken CSP" >&2
  exit 1
fi

# '#' delimiter: the origin contains '/'. Safe to interpolate — the allowlist
# above rules out '&', '#' and every other character sed would reinterpret.
sed -i "s#__AUTH_ORIGIN__#${AUTH_ORIGIN}#g" "$CONF"
echo "30-csp.sh: CSP connect-src allows ${AUTH_ORIGIN}"

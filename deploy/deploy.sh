#!/bin/bash
# Deploys a finyo release on the VPS. Piped over SSH by the release workflow's
# deploy job, so the server always executes the version from the released tag:
#   bash -s -- <tag> <version> <release_be> <release_fe>
#
# The shared services (Postgres, Keycloak, Caddy, monitoring) are operated
# centrally by the vps-platform repo. finyo runs ONLY be/fe on the shared `edge`
# network. A routine release just updates those two images.
#
# The platform registration artifacts — deploy/sites/finyo.caddy,
# keycloak/finyo-realm.prod.json, deploy/finyo.yml — change rarely. When they
# do, register them deliberately (not on every code deploy) by pushing them to
# /opt/platform/{caddy/sites,keycloak/import,prometheus/targets} and running
# ~/vps-platform/deploy/lib/onboard.sh finyo. See vps-platform docs/ONBOARD_APP.md.
set -euo pipefail

TAG="$1"
VERSION="$2"
RELEASE_BE="$3"
RELEASE_FE="$4"
DEPLOY_DIR="${DEPLOY_DIR:-$HOME/finyo}"

compose() {
	docker compose -p finyo -f compose.prod.yml --env-file .env.prod "$@"
}

set_version() {
	local key="$1"
	if grep -q "^${key}=" .env.prod; then
		sed -i "s|^${key}=.*|${key}=${VERSION}|" .env.prod
	else
		echo "${key}=${VERSION}" >>.env.prod
	fi
}

cd "$DEPLOY_DIR"

echo "==> Checking out ${TAG}"
git fetch --tags --force origin
git checkout -f "$TAG"

[ "$RELEASE_BE" = "true" ] && set_version FINYO_BE_VERSION
[ "$RELEASE_FE" = "true" ] && set_version FINYO_FE_VERSION
# be/fe need the shared IDP subdomain; ensure it is set (idempotent)
grep -q '^AUTH_DOMAIN=' .env.prod || echo 'AUTH_DOMAIN=auth.frama-maschinenhandel.ch' >>.env.prod

# shared platform network (idempotent; created by the platform)
docker network create edge 2>/dev/null || true

echo "==> Pulling images and restarting be/fe"
compose pull --quiet
compose up -d

if [ "$RELEASE_BE" = "true" ]; then
	echo "==> Waiting for finyo-be to become healthy"
	cid=$(compose ps -q finyo-be)
	status=starting
	for _ in $(seq 1 30); do
		status=$(docker inspect -f '{{.State.Health.Status}}' "$cid")
		[ "$status" = "healthy" ] && break
		sleep 6
	done
	if [ "$status" != "healthy" ]; then
		echo "!! finyo-be did not become healthy (status: $status)" >&2
		compose logs --tail 50 finyo-be >&2
		exit 1
	fi
fi

echo "==> Smoke test via public URL"
DOMAIN=$(grep '^FINYO_DOMAIN=' .env.prod | cut -d= -f2)
curl -sfo /dev/null --max-time 15 "https://${DOMAIN}"

docker image prune -f >/dev/null

echo "==> Deployed ${TAG} successfully"

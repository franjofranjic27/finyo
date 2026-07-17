# Deployment

finyo runs on a shared single-host **platform**: the reverse proxy (Caddy),
identity provider (Keycloak), PostgreSQL and the observability stack are
operated centrally by the [`vps-platform`](https://github.com/franjofranjic27/vps-platform)
repo. finyo itself ships only its **backend and frontend** and plugs into that
platform.

| Concern | Where |
|---|---|
| Caddy (TLS, ingress on 80/443), Keycloak, Postgres, Grafana/Prometheus/Loki | `vps-platform` |
| `finyo-be`, `finyo-fe` | this repo (`compose.prod.yml`) |

## Architecture

- `finyo-be` / `finyo-fe` join the shared external `edge` Docker network and
  **publish no ports**. They are reached only through the platform's Caddy.
- The app is served at `finyo.frama-maschinenhandel.ch`; the platform Caddy
  routes `/api/*` → `finyo-be`, everything else → `finyo-fe`, via finyo's site
  snippet `deploy/sites/finyo.caddy`.
- Auth lives on the **shared subdomain** `auth.frama-maschinenhandel.ch` (one
  Keycloak, one realm per app — no `/auth` path prefix). The backend validates
  the token issuer against that public URL and fetches keys internally over
  `edge`:
  - `KEYCLOAK_ISSUER_URI=https://auth.frama-maschinenhandel.ch/realms/finyo`
  - `KEYCLOAK_JWK_URI=http://keycloak:8080/realms/finyo/protocol/openid-connect/certs`
- The frontend image is environment-agnostic: at container start
  `finyo-fe/docker/40-runtime-config.sh` writes `/config.js` from `KEYCLOAK_URL`,
  and `finyo-fe/docker/30-csp.sh` substitutes the same URL's origin into the
  CSP's `connect-src` (nginx serves the CSP; Caddy passes it through). Keycloak
  is a foreign origin, so a wrong `KEYCLOAK_URL` blocks login at the browser.
- finyo's Postgres data (its schema + the Keycloak database) lives in the shared
  platform Postgres instance.

## Prerequisites

- The `vps-platform` stack is deployed and running on the host (Caddy, Keycloak,
  Postgres, monitoring, the `edge` network and the `/opt/platform/*` mount dirs).
  See its `docs/ONBOARD_APP.md`.
- Released `finyo-be` / `finyo-fe` images on Docker Hub (`release.yml`; requires
  `DOCKERHUB_USERNAME` / `DOCKERHUB_TOKEN`).
- A DNS A record for `finyo.frama-maschinenhandel.ch` pointing to the host.

## Onboarding finyo onto the platform (once, or when infra changes)

finyo registers with the platform by pushing three artifacts to the platform
mount dirs and running the platform's `onboard.sh` (reloads Caddy, syncs the
realm, reloads Prometheus — no restarts):

```bash
# on the VPS, from ~/finyo
cp deploy/sites/finyo.caddy /opt/platform/caddy/sites/finyo.caddy
cp deploy/finyo.yml         /opt/platform/prometheus/targets/finyo.yml

# The realm MUST be resolved first. finyo-realm.prod.json carries ${FINYO_DOMAIN}
# in redirectUris, webOrigins and post.logout.redirect.uris, and nothing on the
# platform substitutes it: the shared Keycloak has no FINYO_DOMAIN in its env, and
# keycloak-config-cli runs with substitution off. Copied raw, the literal string
# lands in the client and every login dies with "Invalid parameter: redirect_uri" —
# while the sync still reports success.
set -a; . .env.prod; set +a
sed "s|\${FINYO_DOMAIN}|${FINYO_DOMAIN}|g" keycloak/finyo-realm.prod.json > /tmp/finyo-realm.json
grep -c FINYO_DOMAIN /tmp/finyo-realm.json   # MUST print 0 — if not, stop here
cp /tmp/finyo-realm.json /opt/platform/keycloak/import/finyo-realm.json

~/vps-platform/deploy/lib/onboard.sh finyo

# then bring up be/fe (env-file carries FINYO_DOMAIN, AUTH_DOMAIN, DB/versions)
docker compose -p finyo -f compose.prod.yml --env-file .env.prod up -d
```

Verify the client survived — this must answer `302`, not `400`:

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  "https://${AUTH_DOMAIN}/realms/finyo/protocol/openid-connect/auth?client_id=finyo-ui&redirect_uri=https%3A%2F%2F${FINYO_DOMAIN}%2F&response_type=code&scope=openid"
```

These artifacts change rarely, so this is a **deliberate step on infra change**,
not part of every code deploy.

Create application users in the Keycloak admin console
(`https://auth.frama-maschinenhandel.ch/admin`, realm `finyo`) and assign the
`user` realm role — without it the API rejects all requests.

## Automated deployment (routine releases)

Pushing a release tag deploys automatically: the release workflow builds and
publishes the images, then over SSH runs `deploy/deploy.sh`, which checks out
the tag, updates the `FINYO_BE_VERSION` / `FINYO_FE_VERSION` pins in `.env.prod`,
ensures `AUTH_DOMAIN`, pulls the images and restarts **only be/fe** on `edge`,
then verifies the backend healthcheck and the public URL. It does not touch the
shared services.

Required repository secrets:

| Secret | Value |
|---|---|
| `VPS_HOST` | server IP or hostname |
| `VPS_USER` | SSH user (e.g. `root`) |
| `VPS_SSH_KEY` | private ed25519 deploy key; public key in the server's `~/.ssh/authorized_keys` |

The repository must be cloned at `~/finyo` on the server (override with
`DEPLOY_DIR`). `.env.prod` lives on the server (never committed) and must carry
`FINYO_DOMAIN`, `AUTH_DOMAIN`, `DB_PASSWORD`, `DOCKERHUB_USERNAME` and the
version pins.

## Monitoring

Operated centrally by `vps-platform` (Grafana, Prometheus, Loki, Alloy,
node_exporter, cAdvisor). finyo only ships its scrape target
`deploy/finyo.yml`; the backend exposes `/actuator/prometheus` on `edge`.
Grafana: `https://grafana.frama-maschinenhandel.ch`.

## Operations

```bash
# restart just finyo's be/fe (shared services are unaffected)
docker compose -p finyo -f compose.prod.yml --env-file .env.prod up -d
docker compose -p finyo -f compose.prod.yml --env-file .env.prod down

# update to a new release
docker compose -p finyo -f compose.prod.yml --env-file .env.prod pull
docker compose -p finyo -f compose.prod.yml --env-file .env.prod up -d
```

Database backups (finyo + keycloak) are handled at the platform level — see
`vps-platform/backup/` (verified snapshot + off-site archiving). Pin
`FINYO_BE_VERSION` / `FINYO_FE_VERSION` in `.env.prod` for reproducible deploys.

## Social login (Google)

Login via Google is configured in Keycloak (Identity Brokering), no code changes
needed:

1. Google Cloud Console → OAuth 2.0 client, authorized redirect URI:
   `https://auth.frama-maschinenhandel.ch/realms/finyo/broker/google/endpoint`
2. Keycloak admin console → realm `finyo` → Identity Providers → Google → enter
   client ID and secret.

The Google client secret lives only in Keycloak (not in the realm export), so a
realm re-sync never deletes the Google provider (the platform onboard uses
`managed=no-delete`). New broker users get no realm role by default; the API
stays closed to them until an admin assigns the `user` role.

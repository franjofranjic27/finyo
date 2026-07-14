# Deployment

Production runs the released Docker Hub images on a single host via
`compose.prod.yml`. Only Caddy is exposed (ports 80/443); it terminates TLS
with automatic Let's Encrypt certificates and routes by path on one domain:

| Path | Service |
|---|---|
| `/auth/*` | Keycloak (`KC_HTTP_RELATIVE_PATH=/auth`) |
| `/api/*` | finyo-be |
| everything else | finyo-fe |

Keycloak runs in production mode (`start`) with its own `keycloak` database
in the shared Postgres instance — users and sessions survive restarts. The
realm is imported on first start from `keycloak/finyo-realm.prod.json`;
`${FINYO_DOMAIN}` placeholders in that file are resolved from the
environment by Keycloak's importer.

> **Realm changes don't reach an existing installation.** `--import-realm`
> skips realms that already exist, so edits to `finyo-realm.prod.json` only
> apply to fresh installs. Apply them manually in the admin console
> (`https://<domain>/auth/admin`) — e.g. for the session/theme settings
> introduced together with the custom login theme:
> Realm settings → Sessions → *SSO Session Idle* = 14 days, *SSO Session
> Max* = 30 days; Realm settings → Tokens → *Revoke Refresh Token* = On,
> *Refresh Token Max Reuse* = 0; Realm settings → Themes → *Login theme*
> = `finyo`.
> The theme files themselves (`keycloak/themes/finyo`) arrive with the
> checked-out release tag and are volume-mounted; a `compose up -d` after
> deploy makes the theme selectable.

The frontend image is environment-agnostic: at container start an nginx
entrypoint script (`finyo-fe/docker/40-runtime-config.sh`) writes
`/config.js` from `KEYCLOAK_URL` / `KEYCLOAK_CLIENT_ID`, which the app reads
before falling back to build-time defaults.

## Prerequisites

- A host with Docker + Docker Compose (a 4 GB VPS is sufficient)
- A DNS A/AAAA record for your domain pointing to the host
- Released images on Docker Hub (see `release.yml`; requires the
  `DOCKERHUB_USERNAME` / `DOCKERHUB_TOKEN` repository secrets)

## First deployment

```bash
git clone https://github.com/franjofranjic27/finyo.git && cd finyo
cp .env.prod.example .env.prod       # fill in domain, passwords, Docker Hub user
docker compose -f compose.prod.yml --env-file .env.prod up -d
```

Verify: `https://<domain>` serves the app, `https://<domain>/auth` serves
Keycloak. Create application users in the Keycloak admin console
(`https://<domain>/auth/admin`, bootstrap admin from `.env.prod`) and assign
them the `user` realm role — without it the API rejects all requests.

## Automated deployment

Pushing a release tag deploys automatically: after building and publishing
the images, the release workflow's `deploy` job connects to the VPS over
SSH, checks out the tag, updates the version pins in `.env.prod`, pulls the
images, restarts the stack (`deploy/deploy.sh`) and verifies the backend
healthcheck plus the public URL.

Required repository secrets:

| Secret | Value |
|---|---|
| `VPS_HOST` | server IP or hostname |
| `VPS_USER` | SSH user (e.g. `root`) |
| `VPS_SSH_KEY` | private ed25519 deploy key; public key in the server's `~/.ssh/authorized_keys` |

The repository must be cloned at `~/finyo` on the server (override with
`DEPLOY_DIR`).

## Monitoring

`monitoring/` contains an independent compose project (Grafana, Prometheus,
Loki, Alloy, node_exporter, cAdvisor) served through the same Caddy at its
own subdomain — see `monitoring/README.md`.

## Operations

```bash
# stop (data survives in the postgres_data volume)
docker compose -f compose.prod.yml --env-file .env.prod down

# start again
docker compose -f compose.prod.yml --env-file .env.prod up -d

# update to a new release
docker compose -f compose.prod.yml --env-file .env.prod pull
docker compose -f compose.prod.yml --env-file .env.prod up -d

# backup the databases (finyo + keycloak)
docker compose -f compose.prod.yml --env-file .env.prod exec postgres \
  pg_dumpall -U finyo > finyo-backup-$(date +%F).sql
```

Pin `FINYO_BE_VERSION` / `FINYO_FE_VERSION` in `.env.prod` to a release tag
for reproducible deploys; unset they default to `latest`.

## Social login (Google)

Login via Google is configured in Keycloak (Identity Brokering), no code
changes needed:

1. Google Cloud Console → create an OAuth 2.0 client, redirect URI:
   `https://<domain>/auth/realms/finyo/broker/google/endpoint`
2. Keycloak admin console → realm `finyo` → Identity Providers → Google →
   enter client ID and secret.

New broker users get no realm role by default; the API stays closed to them
until an admin assigns the `user` role.

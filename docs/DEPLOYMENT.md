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

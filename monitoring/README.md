# Monitoring

Independent compose project for the VPS: **Grafana** (dashboards),
**Prometheus** (metrics, 15d retention), **Loki** (logs, 14d retention),
**Alloy** (ships all container logs to Loki), **node_exporter** (host
metrics) and **cAdvisor** (per-container metrics). Prometheus also scrapes
the backend's `/actuator/prometheus`.

Grafana is the only exposed piece — routed through the app stack's Caddy
via the shared external `edge` network. Everything else stays internal.

## Setup

1. DNS: add an A record for the Grafana domain (e.g. `grafana`) pointing to
   the server IP.
2. In the app stack's `.env.prod`, set `GRAFANA_DOMAIN=grafana.example.com`
   and restart Caddy so it picks up the new virtual host:

   ```bash
   docker compose -f compose.prod.yml --env-file .env.prod up -d caddy
   ```

3. Start the monitoring stack:

   ```bash
   cd monitoring
   cp .env.example .env        # set GRAFANA_DOMAIN + GRAFANA_ADMIN_PASSWORD
   docker network create edge  # no-op if it already exists
   docker compose up -d
   ```

4. Log in at `https://<GRAFANA_DOMAIN>` with the admin credentials from
   `.env`. Prometheus and Loki are pre-provisioned as datasources.

## Dashboards

Import via Dashboards → New → Import using these grafana.com IDs:

| ID | Dashboard | Source |
|---|---|---|
| 1860 | Node Exporter Full (host CPU/RAM/disk/network) | node_exporter |
| 14282 | cAdvisor exporter (per-container resources) | cAdvisor |
| 4701 | JVM (Micrometer) — heap, GC, threads of finyo-be | finyo-be |

Logs: Explore → datasource **Loki**, query e.g. `{service="finyo-be"}`.

## Operations

```bash
cd monitoring
docker compose ps                  # status
docker compose pull && docker compose up -d   # update images
docker compose down                # stop (data survives in volumes)
```

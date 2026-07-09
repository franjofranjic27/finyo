#!/bin/bash
# Creates the Keycloak database next to the finyo one.
# Runs only on the very first start with an empty postgres_data volume.
set -euo pipefail

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres <<-EOSQL
	CREATE USER keycloak WITH PASSWORD '${KC_DB_PASSWORD}';
	CREATE DATABASE keycloak OWNER keycloak;
EOSQL

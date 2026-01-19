#!/bin/bash
set -e

# Create keycloak database
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE keycloak_db;
    GRANT ALL PRIVILEGES ON DATABASE keycloak_db TO finance_user;
EOSQL

echo "Keycloak database created successfully!"

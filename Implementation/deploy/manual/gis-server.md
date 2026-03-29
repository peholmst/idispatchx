# GIS Server — Manual Installation (Debian / Ubuntu)

This document covers installing and running the GIS Server on a plain Debian or Ubuntu system without Docker or any container runtime.

## Contents

1. [Prerequisites](#1-prerequisites)
2. [Install Java 25 (JRE)](#2-install-java-25-jre)
3. [Install PostgreSQL 17 with PostGIS 3.5](#3-install-postgresql-17-with-postgis-35)
4. [Set Up the GIS Database](#4-set-up-the-gis-database)
5. [Obtain the GIS Server JAR](#5-obtain-the-gis-server-jar)
6. [Create a Dedicated System User](#6-create-a-dedicated-system-user)
7. [Prepare the Tile Directory](#7-prepare-the-tile-directory)
8. [Configure the Service](#8-configure-the-service)
9. [Create a systemd Service Unit](#9-create-a-systemd-service-unit)
10. [Start and Enable the Service](#10-start-and-enable-the-service)
11. [Verify the Installation](#11-verify-the-installation)
12. [Environment Variable Reference](#12-environment-variable-reference)

---

## 1. Prerequisites

Before you begin, ensure the following are in place:

- A Debian 12 (Bookworm) or Ubuntu 24.04 (Noble) server with root or `sudo` access.
- A running OIDC provider (e.g., Keycloak) with a client registration for the GIS Server.
- Raster tile data produced by the **GIS Data Importer** tool (see `Implementation/tools/gis-data-importer/README.md`). The tile directory must be accessible on this host.
- Outbound network access to the OIDC provider's JWKS endpoint.

---

## 2. Install Java 25 (JRE)

The GIS Server requires Java 25. Use the Eclipse Temurin distribution from Adoptium.

```bash
# Install prerequisites
sudo apt-get update
sudo apt-get install -y wget apt-transport-https gnupg

# Add the Adoptium GPG key and repository
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
  | sudo gpg --dearmor -o /etc/apt/trusted.gpg.d/adoptium.gpg
echo "deb https://packages.adoptium.net/artifactory/deb $(lsb_release -sc) main" \
  | sudo tee /etc/apt/sources.list.d/adoptium.list

# Install Temurin 25 JRE
sudo apt-get update
sudo apt-get install -y temurin-25-jre
```

Verify the installation:

```bash
java -version
# Expected: openjdk version "25" ...
```

---

## 3. Install PostgreSQL 17 with PostGIS 3.5

```bash
# Add the PostgreSQL APT repository
sudo apt-get install -y curl ca-certificates
sudo install -d /usr/share/postgresql-common/pgdg
curl -so /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc \
  --fail https://www.postgresql.org/media/keys/ACCC4CF8.asc
sh -c 'echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] \
  https://apt.postgresql.org/pub/repos/apt $(lsb_release -sc)-pgdg main" \
  > /etc/apt/sources.list.d/pgdg.list'

# Install PostgreSQL 17 and PostGIS 3.5
sudo apt-get update
sudo apt-get install -y postgresql-17 postgresql-17-postgis-3
```

Start and enable PostgreSQL:

```bash
sudo systemctl enable --now postgresql
```

---

## 4. Set Up the GIS Database

All commands in this section run as the `postgres` system user.

### 4.1 Create the database user

```bash
sudo -u postgres psql -c "CREATE USER gis WITH PASSWORD 'changeme';"
```

Replace `changeme` with a strong, randomly generated password. See [Section 8](#8-configure-the-service) for how to supply the password to the GIS Server.

### 4.2 Create the database

```bash
sudo -u postgres psql -c "CREATE DATABASE gisdb OWNER gis ENCODING 'UTF8';"
```

### 4.3 Install PostGIS extensions

The GIS Server's Flyway migrations will attempt to create the `postgis` and `pg_trgm` extensions on startup. These require superuser privileges to install. Pre-create them as the `postgres` superuser so the application user does not need elevated database privileges:

```bash
sudo -u postgres psql -d gisdb -c "CREATE EXTENSION IF NOT EXISTS postgis;"
sudo -u postgres psql -d gisdb -c "CREATE EXTENSION IF NOT EXISTS pg_trgm;"
```

### 4.4 Grant schema privileges

The GIS Server manages its own schema via Flyway. Grant the `gis` user the ability to create objects in the database:

```bash
sudo -u postgres psql -d gisdb -c "GRANT CREATE ON DATABASE gisdb TO gis;"
```

### 4.5 Verify connectivity

```bash
psql -h localhost -U gis -d gisdb -c "SELECT PostGIS_Version();"
# Should print the installed PostGIS version
```

---

## 5. Obtain the GIS Server JAR

### Option A: Build from source

On a machine with Java 25 JDK and Maven installed, run from the `Implementation/` directory:

```bash
./mvnw package -pl servers/gis-server -am -DskipTests
```

The executable JAR is produced at:

```
servers/gis-server/target/gis-server-exec.jar
```

Copy the JAR to the target server:

```bash
scp servers/gis-server/target/gis-server-exec.jar user@server:/opt/gis-server/gis-server-exec.jar
```

### Option B: Use a pre-built artifact

Obtain `gis-server-exec.jar` from your build pipeline or release package and place it on the server.

---

## 6. Create a Dedicated System User

Run the GIS Server under a non-privileged system account:

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin gis-server
```

Create the application directory and set ownership:

```bash
sudo mkdir -p /opt/gis-server
sudo cp gis-server-exec.jar /opt/gis-server/gis-server-exec.jar
sudo chown -R root:gis-server /opt/gis-server
sudo chmod 750 /opt/gis-server
sudo chmod 640 /opt/gis-server/gis-server-exec.jar
```

---

## 7. Prepare the Tile Directory

The GIS Server reads raster tile data from a directory populated by the GIS Data Importer. Create the directory and assign it to the `gis-server` user:

```bash
sudo mkdir -p /var/lib/gis-server/tiles
sudo chown -R gis-server:gis-server /var/lib/gis-server
```

Then populate it using the GIS Data Importer tool (see `Implementation/tools/gis-data-importer/README.md`). The importer can write directly to this path, or you can import elsewhere and move the output here.

The directory must be readable by the `gis-server` user at runtime.

---

## 8. Configure the Service

Create an environment file that holds the runtime configuration. This file must be readable only by root and the `gis-server` user because it may contain a database password.

```bash
sudo mkdir -p /etc/gis-server
sudo tee /etc/gis-server/env > /dev/null <<'EOF'
# HTTP server
GIS_SERVER_PORT=8080

# Tile storage
GIS_TILE_DIR=/var/lib/gis-server/tiles

# URL context path (empty = subdomain routing; set to /gis for context-path routing)
GIS_CONTEXT_PATH=

# CORS: comma-separated allowed origins, or leave empty to disable
GIS_CORS_ALLOWED_ORIGINS=

# Database
GIS_DB_URL=jdbc:postgresql://localhost:5432/gisdb
GIS_DB_USER=gis
GIS_DB_PASSWORD=changeme
# GIS_DB_POOL_SIZE=10

# OIDC provider
GIS_OIDC_ISSUER=https://your-oidc-provider.example.com
GIS_OIDC_CLIENT_ID=gis-server
# GIS_OIDC_JWKS_URL=   (leave unset to use the provider's .well-known/jwks.json)
EOF

sudo chown root:gis-server /etc/gis-server/env
sudo chmod 640 /etc/gis-server/env
```

**Storing the password in a file instead of the environment variable**

For higher security, write the database password to a separate file and reference it with `GIS_DB_PASSWORD_FILE` instead of `GIS_DB_PASSWORD`:

```bash
sudo tee /etc/gis-server/db-password > /dev/null <<'EOF'
changeme
EOF
sudo chown root:gis-server /etc/gis-server/db-password
sudo chmod 640 /etc/gis-server/db-password
```

In `/etc/gis-server/env`, replace `GIS_DB_PASSWORD=changeme` with:

```
GIS_DB_PASSWORD_FILE=/etc/gis-server/db-password
```

---

## 9. Create a systemd Service Unit

```bash
sudo tee /etc/systemd/system/gis-server.service > /dev/null <<'EOF'
[Unit]
Description=iDispatchX GIS Server
After=network.target postgresql.service
Requires=postgresql.service

[Service]
Type=simple
User=gis-server
Group=gis-server
WorkingDirectory=/opt/gis-server
EnvironmentFile=/etc/gis-server/env
ExecStart=/usr/bin/java --enable-preview -Djava.awt.headless=true -jar /opt/gis-server/gis-server-exec.jar
Restart=on-failure
RestartSec=5
TimeoutStopSec=30

# Harden the service
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ReadWritePaths=/var/lib/gis-server
ProtectHome=true

[Install]
WantedBy=multi-user.target
EOF
```

Reload systemd to pick up the new unit:

```bash
sudo systemctl daemon-reload
```

---

## 10. Start and Enable the Service

```bash
# Enable the service to start at boot
sudo systemctl enable gis-server

# Start the service now
sudo systemctl start gis-server
```

Follow the logs to confirm successful startup:

```bash
sudo journalctl -u gis-server -f
```

A successful start produces log lines showing the Flyway migration completing and the Javalin HTTP server listening on the configured port.

---

## 11. Verify the Installation

The `/health` endpoint is accessible only on the internal network (it must **not** be exposed through the public reverse proxy — see [Security NFR](../../../Spec/NonFunctionalRequirements/Security.md)).

```bash
curl -s http://localhost:8080/health | python3 -m json.tool
```

Expected response when all components are healthy:

```json
{
  "status": "UP",
  "components": {
    "database": { "status": "UP" },
    "tileDirectory": { "status": "UP", "layers": ["orthophoto"] }
  }
}
```

If `status` is `DOWN`, inspect the `error` field in the relevant component and check `journalctl -u gis-server` for details.

---

## 12. Environment Variable Reference

| Variable | Required | Default | Description |
|---|---|---|---|
| `GIS_SERVER_PORT` | No | `8080` | HTTP port the server listens on |
| `GIS_TILE_DIR` | **Yes** | — | Filesystem path to the tile data directory |
| `GIS_CONTEXT_PATH` | No | `` (empty) | URL prefix for reverse proxy context-path routing (e.g. `/gis`). Must start with `/` and have no trailing `/`. Leave empty for subdomain routing. |
| `GIS_CORS_ALLOWED_ORIGINS` | No | `` (empty) | Comma-separated list of allowed CORS origins. Empty disables CORS. |
| `GIS_DB_URL` | **Yes** | — | JDBC connection URL (e.g. `jdbc:postgresql://localhost:5432/gisdb`) |
| `GIS_DB_USER` | **Yes** | — | Database username |
| `GIS_DB_PASSWORD` | See note | — | Database password (use this or `GIS_DB_PASSWORD_FILE`) |
| `GIS_DB_PASSWORD_FILE` | See note | — | Path to a file containing the database password |
| `GIS_DB_POOL_SIZE` | No | `10` | HikariCP connection pool size (1–100) |
| `GIS_OIDC_ISSUER` | **Yes** | — | OIDC provider issuer URL |
| `GIS_OIDC_CLIENT_ID` | **Yes** | — | OIDC client ID registered for the GIS Server |
| `GIS_OIDC_JWKS_URL` | No | `{issuer}/.well-known/jwks.json` | Override for the JWKS endpoint URL |

> **Note:** Exactly one of `GIS_DB_PASSWORD` or `GIS_DB_PASSWORD_FILE` must be provided.

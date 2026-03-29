# NGINX Reverse Proxy — Manual Setup (Debian / Ubuntu)

This document covers installing and configuring NGINX as a reverse proxy for the GIS Server on a plain Debian or Ubuntu system.

For the authoritative requirements driving this configuration, see:
- [ADR-0004: Shared Reverse Proxy for CAD and GIS Servers](../../../Spec/ADR/ADR-0004-shared-reverse-proxy.md)
- [Security NFR](../../../Spec/NonFunctionalRequirements/Security.md)

## Contents

1. [Install NGINX](#1-install-nginx)
2. [Routing Strategies](#2-routing-strategies)
3. [Option A — Subdomain Routing](#3-option-a--subdomain-routing)
4. [Option B — Context-Path Routing](#4-option-b--context-path-routing)
5. [Enable TLS with Let's Encrypt](#5-enable-tls-with-lets-encrypt)
6. [Verify the Configuration](#6-verify-the-configuration)
7. [Security Requirements](#7-security-requirements)
8. [Multiple GIS Server Instances (Load Balancing)](#8-multiple-gis-server-instances-load-balancing)

---

## 1. Install NGINX

```bash
sudo apt-get update
sudo apt-get install -y nginx
sudo systemctl enable --now nginx
```

Verify it is running:

```bash
sudo systemctl status nginx
```

---

## 2. Routing Strategies

The GIS Server supports two URL routing strategies when deployed behind a reverse proxy. Choose one and configure the GIS Server and NGINX to match.

| Strategy | GIS Server `GIS_CONTEXT_PATH` | Example URL |
|---|---|---|
| **Subdomain** (recommended) | `` (empty) | `https://gis.example.com/wmts/...` |
| **Context-path** | `/gis` | `https://example.com/gis/wmts/...` |

The examples below use `gis.example.com` and `example.com` as placeholders. Replace them with your actual hostnames.

---

## 3. Option A — Subdomain Routing

In this strategy, the GIS Server is reachable on its own subdomain (e.g. `gis.example.com`). Set `GIS_CONTEXT_PATH=` (empty) on the GIS Server.

### 3.1 Create the site configuration

```bash
sudo tee /etc/nginx/sites-available/gis-server.conf > /dev/null <<'EOF'
upstream gis_servers {
    server 127.0.0.1:8080;
    # Add more backend addresses here for load balancing (see Section 8)
}

server {
    listen 80;
    server_name gis.example.com;

    # /wmts/ — raster tile endpoint
    location /wmts/ {
        proxy_pass         http://gis_servers;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_set_header   Authorization     $http_authorization;
        proxy_pass_request_headers on;
    }

    # /api/v1/ — geocoding and other REST endpoints
    location /api/v1/ {
        proxy_pass         http://gis_servers;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_set_header   Authorization     $http_authorization;
        proxy_pass_request_headers on;
    }

    # All other paths (including /health) are intentionally not proxied.
    # NGINX returns 404 because no location block matches them.
    # This is required by the Security NFR.
}
EOF
```

### 3.2 Enable the site

```bash
sudo ln -s /etc/nginx/sites-available/gis-server.conf /etc/nginx/sites-enabled/gis-server.conf
sudo nginx -t && sudo systemctl reload nginx
```

---

## 4. Option B — Context-Path Routing

In this strategy, the GIS Server is reachable under a path prefix on a shared domain (e.g. `https://example.com/gis/...`). Set `GIS_CONTEXT_PATH=/gis` on the GIS Server.

### 4.1 Create the site configuration

```bash
sudo tee /etc/nginx/sites-available/idispatchx.conf > /dev/null <<'EOF'
upstream gis_servers {
    server 127.0.0.1:8080;
    # Add more backend addresses here for load balancing (see Section 8)
}

server {
    listen 80;
    server_name example.com;

    # /gis/ — all GIS Server endpoints (WMTS and REST)
    location /gis/ {
        proxy_pass         http://gis_servers;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_set_header   Authorization     $http_authorization;
        proxy_pass_request_headers on;
    }

    # All other paths (including /gis/health) are intentionally not proxied.
}
EOF
```

> **Important:** With context-path routing, the GIS Server health endpoint is at `/gis/health` internally. Do **not** add a location block for `/gis/health` in the public server configuration.

### 4.2 Enable the site

```bash
sudo ln -s /etc/nginx/sites-available/idispatchx.conf /etc/nginx/sites-enabled/idispatchx.conf
sudo nginx -t && sudo systemctl reload nginx
```

---

## 5. Enable TLS with Let's Encrypt

TLS termination at the reverse proxy is strongly recommended for production. The `certbot` tool automates certificate issuance and renewal.

### 5.1 Install Certbot

```bash
sudo apt-get install -y certbot python3-certbot-nginx
```

### 5.2 Obtain a certificate

For subdomain routing:

```bash
sudo certbot --nginx -d gis.example.com
```

For context-path routing on a shared domain:

```bash
sudo certbot --nginx -d example.com
```

Certbot modifies the NGINX site configuration to add TLS and redirect HTTP to HTTPS. After it completes, verify the updated configuration and reload NGINX:

```bash
sudo nginx -t && sudo systemctl reload nginx
```

### 5.3 Automatic renewal

Certbot installs a systemd timer that renews certificates before they expire. Confirm it is active:

```bash
sudo systemctl status certbot.timer
```

---

## 6. Verify the Configuration

### 6.1 Test the NGINX configuration file

```bash
sudo nginx -t
```

### 6.2 Confirm the proxy forwards requests

With the GIS Server running (see `gis-server.md`), send a request through the proxy:

```bash
# Subdomain routing — replace with your domain
curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer <token>" \
  http://gis.example.com/api/v1/geocode?q=Helsinki

# Context-path routing
curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer <token>" \
  http://example.com/gis/api/v1/geocode?q=Helsinki
```

Expect `200` for a valid token or `401` for a missing/invalid token. A `502` means NGINX cannot reach the GIS Server backend.

### 6.3 Confirm `/health` is not reachable through the proxy

```bash
# Subdomain routing
curl -s -o /dev/null -w "%{http_code}" http://gis.example.com/health
# Must return 404, not 200

# Context-path routing
curl -s -o /dev/null -w "%{http_code}" http://example.com/gis/health
# Must return 404, not 200
```

The health endpoint must return `200` only when accessed directly on the backend host:

```bash
curl -s http://localhost:8080/health
# Returns 200 with JSON body
```

---

## 7. Security Requirements

The following requirements apply to any reverse proxy configuration and are mandated by the [Security NFR](../../../Spec/NonFunctionalRequirements/Security.md) and [ADR-0004](../../../Spec/ADR/ADR-0004-shared-reverse-proxy.md).

**The `/health` endpoint must never be exposed publicly.**
Do not add a `location /health` or `location /gis/health` block. The health endpoint is for internal infrastructure monitoring only. It must remain accessible solely on the internal network (e.g., for a load balancer's health check probes directed at the backend directly).

**The `Authorization` header must be forwarded.**
JWT bearer tokens are validated by the GIS Server for every authenticated request. The proxy must pass the header through:

```nginx
proxy_set_header Authorization $http_authorization;
proxy_pass_request_headers on;
```

Without this, all authenticated requests return `401 Unauthorized`.

**WebSocket upgrade is not required for the GIS Server.**
The GIS Server uses plain HTTP only. Do not add WebSocket upgrade headers to GIS Server location blocks. (The CAD Server, when implemented, will require WebSocket support.)

---

## 8. Multiple GIS Server Instances (Load Balancing)

To run more than one GIS Server instance behind NGINX, list each backend in the `upstream` block. NGINX uses round-robin load balancing by default.

```nginx
upstream gis_servers {
    server 127.0.0.1:8080;
    server 127.0.0.1:8081;
    # Or across multiple hosts:
    # server gis-node-1.internal:8080;
    # server gis-node-2.internal:8080;
}
```

All GIS Server instances must:
- Share the same tile directory (e.g., via NFS mount or a shared volume).
- Connect to the same PostGIS database.
- Be configured with the same OIDC issuer and client ID.

NGINX will route requests to each backend in turn. If one instance is down, NGINX marks it as unavailable after a failed request and retries on the remaining instances.

To enable active health checks (NGINX Plus required) or to tune passive failure detection, refer to the NGINX documentation for the `upstream` module. With the open-source NGINX, passive health checks are used by default: a backend is temporarily removed from rotation after it returns a connection error.

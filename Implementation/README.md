# iDispatchX Implementation Structure

This document describes the directory structure for iDispatchX container implementations.

For authoritative container specifications, see [../Spec/C4/Containers.md](../Spec/C4/Containers.md).


## Directory Overview

```
Implementation/
├── .mvn/wrapper/                  # Maven wrapper configuration
├── servers/                       # Backend services
├── clients/                       # Frontend applications
├── tools/                         # CLI tools
├── shared/                        # Shared code libraries
├── deploy/                        # Deployment configurations
├── mvnw                           # Maven wrapper script (Unix)
├── mvnw.cmd                       # Maven wrapper script (Windows)
├── pom.xml                        # Parent POM for Java modules
└── README.md                      # This file
```


## Namespace Convention

All containers use the base namespace `net.pkhapps.idispatchx` where applicable.

| Container              | Language   | Namespace / Package                        |
|------------------------|------------|--------------------------------------------|
| CAD Server             | Java       | `net.pkhapps.idispatchx.cad`               |
| GIS Server             | Java       | `net.pkhapps.idispatchx.gis`               |
| GIS Data Importer      | Java       | `net.pkhapps.idispatchx.gis.importer`      |
| Shared Java Library    | Java       | `net.pkhapps.idispatchx.common`            |
| Mobile Unit Client     | Kotlin     | `net.pkhapps.idispatchx.mobile`            |
| Dispatcher Client      | TypeScript | `@idispatchx/dispatcher-client`            |
| Admin Client           | TypeScript | `@idispatchx/admin-client`                 |
| Station Alert Client   | Rust       | `idispatchx-station-alert`                 |


## Servers

Backend services that run continuously.

### CAD Server

**Location:** `servers/cad-server/`

The heart of iDispatchX. Manages calls, incidents, and unit status.

- **Tech Stack:** Java 25, Javalin, Jackson, jOOQ, Flyway, Maven
- **Package:** `net.pkhapps.idispatchx.cad`

### GIS Server

**Location:** `servers/gis-server/`

Provides map tiles (WMTS) and geocoding services.

- **Tech Stack:** Java 25, Javalin, Geotools, jOOQ, Flyway, Maven
- **Package:** `net.pkhapps.idispatchx.gis`


## Clients

Frontend applications used by actors.

### Dispatcher Client

**Location:** `clients/dispatcher-client/`

Web application for dispatchers to manage calls, incidents, and units.

- **Tech Stack:** TypeScript, OpenLayers, Vanilla Web Components, Vite
- **Package:** `@idispatchx/dispatcher-client`

### Admin Client

**Location:** `clients/admin-client/`

Web application for system administration.

- **Tech Stack:** TypeScript, Vanilla Web Components, Vite
- **Package:** `@idispatchx/admin-client`

### Station Alert Client

**Location:** `clients/station-alert-client/`

Native Linux application for RaspberryPi-based station alerting.

- **Tech Stack:** Rust, minifb, embedded-graphics, tokio-tungstenite, Cargo
- **Crate:** `idispatchx-station-alert`

### Mobile Unit Client

**Location:** `clients/mobile-unit-client/`

Android application for field units.

- **Tech Stack:** Kotlin, Android Jetpack, OkHttp, AppAuth-Android, Gradle
- **Package:** `net.pkhapps.idispatchx.mobile`


## Tools

Command-line utilities run on demand.

### GIS Data Importer

**Location:** `tools/gis-data-importer/`

Imports map and address data from National Land Survey of Finland.

- **Tech Stack:** Java 25, Geotools, jOOQ, Flyway, Maven
- **Package:** `net.pkhapps.idispatchx.gis.importer`


## Shared Libraries

### Java Common

**Location:** `shared/java-common/`

Shared code used by CAD Server, GIS Server, and GIS Data Importer.

- **Package:** `net.pkhapps.idispatchx.common`

### GIS Database

**Location:** `shared/gis-database/`

Shared Flyway migrations and jOOQ generated code for the GIS database (`gis` schema). Used by GIS Server and GIS Data Importer.

- **Package:** `net.pkhapps.idispatchx.gis.database`


## Deployment

**Location:** `deploy/`

Deployment configurations and scripts.

| Directory              | Purpose                          |
|------------------------|----------------------------------|
| `deploy/docker/`       | Dockerfiles and compose files    |
| `deploy/kubernetes/`   | Kubernetes manifests             |
| `deploy/scripts/`      | Deployment and utility scripts   |


## Building

All build commands assume you are in the `Implementation/` directory.

The repository includes wrapper scripts for Maven and Gradle that download the correct version automatically. No global installation of these tools is required.

### All Java Modules

```bash
./mvnw clean package
```

### Individual Java Modules

```bash
# CAD Server with dependencies
./mvnw -pl servers/cad-server -am package

# GIS Server with dependencies
./mvnw -pl servers/gis-server -am package

# GIS Data Importer with dependencies
./mvnw -pl tools/gis-data-importer -am package
```

### TypeScript Clients

```bash
# Dispatcher Client
cd clients/dispatcher-client && npm install && npm run build

# Admin Client
cd clients/admin-client && npm install && npm run build
```

### Rust Client

```bash
cd clients/station-alert-client && cargo build --release

# Cross-compile for Raspberry Pi
cd clients/station-alert-client && cross build --release --target aarch64-unknown-linux-gnu
```

### Android Client

```bash
cd clients/mobile-unit-client && ./gradlew assembleRelease
```

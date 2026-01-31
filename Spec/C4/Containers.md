# iDispatchX System Containers Specification

This document describes the containers (deployment units) of iDispatchX, which tech stacks they use and how they interact with each other and other entities.

A C4 container diagram is provided in: [Containers.puml](Containers.puml)

All iDispatchX containers are built *without a framework* by design, with the exception of Mobile Unit Client where platform constraints require it. Instead, they utilize the newest features of their respective programming languages and platform APIs to the fullest. This keeps the number of external library dependencies small and the code easier to maintain in the future, as there is no need to upgrade from one framework version to the next.


## Container Responsibility Summary

Here is a summary of the containers of iDispatchX:

* *CAD Server* - operational state and orchestration
* *GIS Server* - spatial data services
* *Clients* - presentation and input
* *Importer* - offline data ingestion

Detailed container descriptions follow later in this document.


## Communication Patterns

The containers of iDispatchX follow common communication patterns:

* Commands (changing state): REST
* Events and subscriptions: WebSocket
* Authentication: OIDC → JWT

All servers have support for OIDC back channel logout so that administrators can immediately disconnect compromised clients from the system. Servers must also be aware of the fact that access tokens might still be valid even though the OIDC session might have ended or the account suspended, and take appropriate actions to deny access.


## Containers

Here are detailed descriptions of each iDispatchX container:


### CAD Server

CAD Server is the heart of iDispatchX. It keeps track of calls, incidents, and unit status. 

CAD Server is the authoritative source of truth for live and archived operational data.

#### Tech Stack

* Programming language: *Java 25*
* REST and WebSocket support: *Javalin*
* Logging: *SLF4j and Logback*
* JSON support: *Jackson*
* JWT support: *Nimbus JOSE + JWT*
* RDBMS access: *jOOQ*
* RDBMS schema management: *Flyway*
* Build system and dependency management: *Maven*

Additional libraries may be added as needed, if the benefit the library provides is greater than the cost of keeping it up to date.

#### Storage

CAD Server keeps active calls, incidents, and unit status in memory. Events are written to a Write-Ahead Log (WAL) file on disk. On startup, CAD Server replays the WAL to restore its memory. In High Availability configurations, the WAL file is shared with a warm standby that is ready to take over if the primary server fails. High availability is discussed in a separate Non-Functional Requirement (NFR) specification document.

CAD Server writes archived calls and incidents to a PostgreSQL database, for future use. It does not use the database for live data.

#### API Endpoints

CAD Server provides REST and WebSocket endpoints for the following containers:

* Dispatcher Client (REST for commands, WS for events)
* Station Alert Client (WS for events)
* Mobile Unit Client (REST for commands, WS for events)
* Admin Client (REST)

All endpoints use JWT bearer authentication.

#### External System Integrations

CAD Server integrates with the following external systems:

* SMTP Server (SMTP)
* SMS Gateway (REST)
* OIDC Provider (JWKS & Back Channel Logout)


### Dispatcher Client

Dispatcher Client is a dual-monitor, progressive single page web application used by dispatchers to enter call and incident data, dispatch units, and track their location and status. Dispatchers are expected to use it from a Chromium-based web browser (like Google Chrome or Microsoft Edge).

#### Tech Stack

* Programming language: *TypeScript*
* Map component: *OpenLayers*
* UI components: *Vanilla Web Components*
* Dependency management: *NPM*
* Build system: *Vite*

Additional libraries may be added as needed, if the benefit the library provides is greater than the cost of keeping it up to date.

#### Container Integrations

Dispatcher Client uses a REST API to send commands to the CAD Server, and a WebSocket connection to subscribe to and receive events from the CAD Server.

Dispatcher Client uses WMTS to fetch tiles from GIS Server, and a REST API for geocoding.

#### External System Integrations

Dispatcher Client integrates with the following external systems:

* OIDC Provider (Authentication & JWT)


### Station Alert Client

Station Alert Client is a native Linux application that runs on a RaspberryPi inside a station. When an alert comes in, it notifies the personnel in quarters through an audible alert signal, text-to-speech, and visually on monitors. 

Station Alert Client uses GPIO to trigger various external hardware, like relays, flashing lights, amplifiers, etc. 

Station Alert Client sends audio to its standard audio output.

Station Alert Client paints its UI directly to the framebuffer, removing the need for a dedicated window manager or desktop system. This keeps the overall RaspberryPi installation clean and simple, and the boot speed fast (Station Alert Client starts automatically when the RaspberryPi boots).

#### Tech Stack

* Programming language: *To be decided*
* Graphics library: *To be decided*
* WebSocket client support: *To be decided*
* OIDC client support: *To be decided*
* Text-to-speech: *Piper TTS*
* Build system: *To be decided*

#### Container Integrations

Station Alert Client uses a WebSocket connection to receive alerts from the CAD Server.

#### External System Integrations

Station Alert Client integrates with the following external systems:

* OIDC Provider (Authentication & JWT)


### Mobile Unit Client

Mobile Unit Client is an Android application that runs on mobile devices that are installed in vehicles, or carried by field personnel. When an alert comes in, the application notifies the unit crew through an audible alert signal. It also shows the alert details on the screen.

The crew uses Mobile Unit Client to acknowledge alerts and update the status of the unit.

While running, Mobile Unit Client sends its location to CAD Server on a fixed, configurable interval while authenticated. Location reporting is designed to continue when the application is in the background.

#### Tech Stack

Mobile Unit Client is the only iDispatchX application that is built on top of a framework.

* Programming language: Kotlin
* Framework: Android Jetpack
* Build system and dependencies: Gradle

Additional libraries may be added as needed, if the benefit the library provides is greater than the cost of keeping it up to date.

#### Container Integration

Mobile Unit Client uses a REST API to send commands to the CAD Server, and a WebSocket connection to receive alerts from the CAD Server.

#### External System Integrations

Mobile Unit Client integrates with the following external systems:

* OIDC Provider (Authentication & JWT)


### Admin Client

Admin Client is a single-monitor, progressive single page web application used by administrators to administer the CAD Server. Administrators are expected to use it from a Chromium-based web browser (like Google Chrome or Microsoft Edge).

#### Tech Stack

* Programming language: *TypeScript*
* UI components: *Vanilla Web Components*
* Dependency management: *NPM*
* Build system: *Vite*

Additional libraries may be added as needed, if the benefit the library provides is greater than the cost of keeping it up to date.

#### Container Integrations

Admin Client uses a REST API to fetch data and send commands to CAD Server. Admin Client does not use any WebSockets.

#### External System Integrations

Admin Client integrates with the following external systems:

* OIDC Provider (Authentication & JWT)


### GIS Server

GIS Server provides dispatchers with map data and geocoding services.

#### Tech Stack

* Programming language: *Java 25*
* REST support: *Javalin*
* Logging: *SLF4j and Logback*
* JSON support: *Jackson*
* JWT support: *Nimbus JOSE + JWT*
* GIS features: *Geotools*
* RDBMS access: *jOOQ*
* RDBMS schema management: *Flyway*
* Build system and dependency management: *Maven*

#### Storage

GIS Server stores raster tiles on the filesystem, and address data in a PostGIS database.

#### API Endpoints

GIS Server provides the following REST endpoints for the Dispatcher Client to use:

* WMTS REST endpoint for raster tiles
* Custom REST endpoint for geocoding services

All endpoints use JWT bearer authentication.

#### External System Integrations

GIS Server integrates with the following external systems:

* OIDC Provider (JWKS & Back Channel Logout)

All data is provided via offline imports.


### GIS Data Importer

GIS Data Importer is a command line tool that imports raster tiles (PNG + world file) and address information (GML and JSON) from the National Land Survey of Finland. 

Administrators download the files manually, then run the GIS Data Importer to parse, process, and store the data.

GIS Data Importer is not a continuously running system. Administrators run it on demand from the command line.

#### Tech Stack

* Programming language: *Java 25*
* Logging: *SLF4j and Logback*
* JSON support: *Jackson*
* GIS features: *Geotools*
* RDBMS access: *jOOQ*
* RDBMS schema management: *Flyway*
* Build system and dependency management: *Maven*

Additional libraries may be added as needed, if the benefit the library provides is greater than the cost of keeping it up to date.

#### Storage

GIS Data Importer writes to the same storages (file system for tiles, PostGIS for address data) as GIS Server.

#### External System Integrations

GIS Data Importer does not integrate directly with any external systems.

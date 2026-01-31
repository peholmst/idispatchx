# iDispatchX Tech Stack Review

**Date:** 2026-01-31
**Purpose:** Review of technology choices against NFRs and domain requirements

---

## 1. Executive Summary

This document provides a comprehensive review of technology decisions across all seven iDispatchX containers. The review evaluates each technology choice against the system's Non-Functional Requirements, particularly the 15-year maintainability goal and the minimal-framework philosophy.

**Key Findings:**

- **6 containers** have fully decided tech stacks
- **1 container** (Station Alert Client) has partially TBD items requiring decisions
- All decided technologies align with NFR constraints
- The minimal-framework philosophy is consistently applied across all containers

---

## 2. Container-by-Container Analysis

### 2.1 Containers with Complete Tech Stacks

#### CAD Server

| Technology | Purpose |
|------------|---------|
| Java 25 | Programming language |
| Javalin | REST and WebSocket support |
| SLF4j + Logback | Logging |
| Jackson | JSON support |
| Nimbus JOSE+JWT | JWT support |
| jOOQ | RDBMS access |
| Flyway | Schema management |
| Maven | Build system |

#### GIS Server

| Technology | Purpose |
|------------|---------|
| Java 25 | Programming language |
| Javalin | REST support |
| SLF4j + Logback | Logging |
| Jackson | JSON support |
| Nimbus JOSE+JWT | JWT support |
| GeoTools | GIS features |
| jOOQ | RDBMS access |
| Flyway | Schema management |
| Maven | Build system |

#### GIS Data Importer

| Technology | Purpose |
|------------|---------|
| Java 25 | Programming language |
| SLF4j + Logback | Logging |
| Jackson | JSON support |
| GeoTools | GIS features |
| jOOQ | RDBMS access |
| Flyway | Schema management |
| Maven | Build system |

#### Dispatcher Client

| Technology | Purpose |
|------------|---------|
| TypeScript | Programming language |
| Leaflet or OpenLayers | Map component (must support EPSG:3067) |
| NPM | Dependency management |
| Vite | Build system |

#### Admin Client

| Technology | Purpose |
|------------|---------|
| TypeScript | Programming language |
| NPM | Dependency management |
| Vite | Build system |

#### Mobile Unit Client

| Technology | Purpose |
|------------|---------|
| Kotlin | Programming language |
| Android Jetpack | Framework (platform-required exception) |
| Gradle | Build system |

---

### 2.2 Container with TBD Items: Station Alert Client

Station Alert Client requires decisions on several technology choices. The following recommendations are based on the container's requirements:

- Native Linux application on Raspberry Pi
- Direct framebuffer rendering (no window manager)
- GPIO control for external hardware
- WebSocket client for CAD Server events
- OIDC authentication
- Text-to-speech via Piper TTS

#### Recommendations

| Component | Recommendation | Rationale |
|-----------|----------------|-----------|
| **Language** | Rust | Memory safety without garbage collection; excellent ARM cross-compilation support; strong async ecosystem; 15-year maintainability aligned |
| **Graphics Library** | minifb + embedded-graphics | Direct framebuffer access; minimal dependencies; well-suited for embedded displays |
| **WebSocket Client** | tokio-tungstenite | Async WebSocket support; integrates with Tokio runtime; actively maintained |
| **OIDC Client** | openidconnect-rs | Standards-compliant OIDC implementation; provider-agnostic per ADR-0001 |
| **Build System** | Cargo + cross | Native Rust tooling; cross simplifies ARM cross-compilation |

**Alternative Language Considerations:**

| Alternative | Pros | Cons |
|-------------|------|------|
| C | Maximum control, smallest binary | Manual memory management, security risks |
| Go | Simple deployment, GC | GC pauses may affect real-time alerts |
| C++ | Performance, mature ecosystem | Complexity, memory safety concerns |

---

## 3. Key Alternatives Analysis

### 3.1 Backend Technologies (Java-based Containers)

#### Programming Language

| Technology | Alternative | Pros | Cons |
|------------|-------------|------|------|
| **Java 25** | Kotlin (JVM) | More concise syntax, null safety built-in | Additional language to maintain, smaller talent pool |
| **Java 25** | Go | Fast compilation, simple deployment, single binary | Less mature enterprise ecosystem, different paradigm |
| **Java 25** | C# (.NET) | Similar maturity, good tooling | Platform alignment concerns, licensing considerations |

#### Web Framework

| Technology | Alternative | Pros | Cons |
|------------|-------------|------|------|
| **Javalin** | Spring Boot | Comprehensive ecosystem, wide adoption | Framework-heavy approach contradicts NFR-Maintainability |
| **Javalin** | Quarkus | Native compilation, fast startup | Still a framework with upgrade burden |
| **Javalin** | Raw Jetty | Even fewer dependencies | More boilerplate code |
| **Javalin** | Helidon SE | Lightweight, Oracle backing | Smaller community |

#### Database Access

| Technology | Alternative | Pros | Cons |
|------------|-------------|------|------|
| **jOOQ** | Hibernate/JPA | Industry standard ORM, wide adoption | "Magic" behavior contradicts NFR explicitness requirement |
| **jOOQ** | JDBC directly | No dependencies | Verbose, error-prone SQL handling |
| **jOOQ** | MyBatis | SQL-centric, flexible | Less type-safe than jOOQ |

#### JSON Processing

| Technology | Alternative | Pros | Cons |
|------------|-------------|------|------|
| **Jackson** | Gson | Simpler API, smaller footprint | Less actively maintained, fewer features |
| **Jackson** | Moshi | Modern design, Kotlin-friendly | Smaller ecosystem |
| **Jackson** | JSON-B (Jakarta) | Standard API | Less feature-rich |

#### JWT Processing

| Technology | Alternative | Pros | Cons |
|------------|-------------|------|------|
| **Nimbus JOSE+JWT** | Auth0 java-jwt | Simpler API | Less comprehensive JOSE support |
| **Nimbus JOSE+JWT** | JJWT | Fluent API | Less comprehensive than Nimbus |

### 3.2 Frontend Technologies (Web Clients)

#### Programming Language

| Technology | Alternative | Pros | Cons |
|------------|-------------|------|------|
| **TypeScript** | JavaScript | No compilation step | No type safety, harder maintenance |
| **TypeScript** | Dart | Strong typing, Flutter option | Smaller web ecosystem |

#### Build System

| Technology | Alternative | Pros | Cons |
|------------|-------------|------|------|
| **Vite** | Webpack | More mature, extensive plugin ecosystem | Complex configuration, slower builds |
| **Vite** | esbuild | Extremely fast | Less feature-rich |
| **Vite** | Parcel | Zero-config | Less control, smaller community |

#### Map Component (Dispatcher Client)

| Technology | Alternative | Pros | Cons |
|------------|-------------|------|------|
| **Leaflet** | OpenLayers | Better native EPSG:3067 support, OSGeo backing, more GIS features | Larger bundle size, steeper learning curve |
| **OpenLayers** | Leaflet | Simpler API, smaller bundle, extensive plugin ecosystem | Requires proj4js for EPSG:3067, less native GIS support |

**Recommendation:** Prefer **OpenLayers** for the Dispatcher Client map component due to:
- Native support for EPSG:3067 projection system
- OSGeo Foundation backing ensures long-term maintenance
- Better alignment with GIS-heavy requirements (WMTS, geocoding integration)
- More comprehensive coordinate transformation capabilities

### 3.3 Mobile Technologies (Mobile Unit Client)

#### Framework

| Technology | Alternative | Pros | Cons |
|------------|-------------|------|------|
| **Android Jetpack** | Flutter | Cross-platform potential | Additional runtime, different paradigm |
| **Android Jetpack** | React Native | JavaScript ecosystem | Performance overhead, bridge complexity |

**Note:** Android Jetpack is the only framework explicitly permitted by the NFR-Maintainability exception for platform constraints.

#### Recommended Additional Libraries

| Purpose | Recommendation | Rationale |
|---------|----------------|-----------|
| WebSocket | OkHttp | Standard Android HTTP client with WebSocket support |
| OIDC | AppAuth-Android | OpenID Foundation reference implementation, provider-agnostic |

---

## 4. Recommendations Summary

### 4.1 Decisions Required

1. **Station Alert Client Tech Stack**
   - Adopt Rust as programming language
   - Use minifb + embedded-graphics for framebuffer rendering
   - Use tokio-tungstenite for WebSocket connectivity
   - Use openidconnect-rs for OIDC authentication
   - Use Cargo + cross for build and cross-compilation

### 4.2 Refinements for Decided Stacks

2. **Dispatcher Client Map Component**
   - Prefer OpenLayers over Leaflet for native EPSG:3067 support
   - OpenLayers provides better alignment with NFR-Internationalization requirements

3. **Admin Client UI Components**
   - If a UI component library is needed, consider Lit or vanilla Web Components
   - Avoid heavy frameworks (React, Vue, Angular) per NFR-Maintainability

4. **Mobile Unit Client Libraries**
   - Use OkHttp for WebSocket communication
   - Use AppAuth-Android for OIDC flows
   - Both are provider-agnostic per ADR-0001

---

## 5. NFR Alignment Assessment

### NFR-Maintainability (15-Year Lifespan)

| Requirement | Assessment |
|-------------|------------|
| Avoid frameworks | All containers comply; Android Jetpack is the only permitted exception |
| Prefer standard libraries | Java containers use standard JDK features extensively |
| Modern language features | Java 25 enables latest language capabilities |
| Explicit over implicit | jOOQ chosen over Hibernate for explicit SQL control |

### NFR-Maintainability (Minimal Dependencies)

| Container | Dependency Assessment |
|-----------|----------------------|
| CAD Server | Minimal; each library serves specific purpose |
| GIS Server | GeoTools is substantial but necessary for GIS operations |
| Dispatcher Client | Minimal; map library is essential |
| Admin Client | Minimal; no UI framework required |
| Mobile Unit Client | Android Jetpack required by platform |
| Station Alert Client (proposed) | Rust ecosystem enables minimal dependencies |

### NFR-Internationalization (EPSG:3067 Support)

| Component | Assessment |
|-----------|------------|
| GeoTools | Native EPSG:3067 support |
| OpenLayers | Native projection support including EPSG:3067 |
| Leaflet | Requires proj4js plugin for EPSG:3067 |

**Recommendation:** OpenLayers better satisfies this NFR.

### ADR-0001 (OIDC Provider Neutrality)

| Container | Assessment |
|-----------|------------|
| Java containers (Nimbus) | Standards-compliant, provider-agnostic |
| Web clients | Standard OIDC flows, no provider lock-in |
| Mobile Unit Client (AppAuth) | OpenID Foundation reference implementation |
| Station Alert Client (openidconnect-rs) | Standards-compliant Rust implementation |

All authentication libraries support standards-compliant OIDC without provider-specific extensions.

---

## 6. Risk Assessment

### Low Risk
- Java 25 ecosystem maturity
- TypeScript long-term viability
- PostgreSQL/PostGIS stability

### Medium Risk
- Rust ecosystem still evolving (Station Alert Client)
- Map library choice (Leaflet vs OpenLayers) affects development experience

### Mitigations
- Station Alert Client Rust choice can be revisited if ecosystem concerns emerge
- Map component is isolated; switching between Leaflet and OpenLayers is feasible

---

## 7. Conclusion

The iDispatchX tech stack demonstrates strong alignment with NFR requirements:

- **Minimal framework philosophy** is consistently applied
- **15-year maintainability** is supported by mature, stable technologies
- **OIDC provider neutrality** is maintained across all containers
- **EPSG:3067 support** is achievable with recommended choices

The primary outstanding decision is the Station Alert Client tech stack. Rust is recommended as the optimal choice balancing performance, safety, and long-term maintainability on embedded ARM platforms.

# ADR Crosscheck Review

**Date:** 2026-01-26
**Reviewed against:** NFRs and C4 Specifications

---

## ADR-0001: OIDC Provider Neutrality

**Status:** Well-aligned

**Supports:**
- C4 Context.md:48 — OIDC provider listed as external system without mandating a specific one
- NFR Security — "centralized identity provider" requirement is provider-agnostic
- NFR Maintainability — avoiding vendor lock-in supports 15-year lifespan goal

**Assessment:** Sound decision. Caching user metadata internally also supports degraded-mode operation if OIDC is briefly unavailable.

---

## ADR-0002: Docker Compose for Development HA Mode

**Status:** Well-aligned

**Supports:**
- NFR Availability — HA mode with warm standby needs to be testable
- C4 Containers.md:59 — "WAL file is shared with a warm standby"

**Assessment:** Appropriate for development. The ADR correctly acknowledges limitations (latency, failure domains).

**Minor gap:** Should clarify which containers run multiple instances:
- CAD Server: warm standby (1 active + 1 standby per NFR Availability:90-94)
- GIS Server: load-balanced replicas (per NFR Availability:97-98)

---

## ADR-0003: Partial HA Failure Testing

**Status:** Well-aligned

**Supports:**
- NFR Availability:66-80 — degraded modes (GIS unavailable, unit locations unavailable)
- NFR Availability:84-94 — CAD Server failover expectations
- NFR Availability:43 — "dispatcher must always know whether the system is running in degraded mode"

**Assessment:** Directly addresses NFR requirements. The "no undeclared implicit dependencies" constraint is critical for realistic failure testing.

---

## ADR-0004: Shared Reverse Proxy

**Status:** Mostly aligned, gaps exist

**Supports:**
- NFR Security:69 — "All data must be encrypted in transit" (TLS termination)
- C4 architecture — CAD Server and GIS Server are separate containers

**Gaps not addressed:**
1. **WebSocket proxying** — Both CAD Server and clients use WebSockets (C4 Containers.md:67,99,130,159). The ADR should specify WebSocket upgrade handling.
2. **OIDC callback URLs** — Reverse proxy affects redirect URIs for authentication flows
3. **JWT forwarding** — Both servers use JWT bearer auth (C4 Containers.md:72,218)

---

## Missing ADRs to Consider

### 1. Session State During Failover

NFR Security:50 states: "Authentication state must not survive server failover unless explicitly intended."

NFR Availability:92-94 describes CAD Server failover via WAL. There's no ADR explaining:
- How active WebSocket sessions are handled during failover
- Whether JWT validation state is replayed from WAL
- Client reconnection behavior

### 2. WAL Format and Semantics

The C4 spec and NFR Availability heavily reference WAL-based state persistence and idempotent commands, but there's no ADR documenting:
- WAL file format decisions
- Replay semantics
- Purging strategy (NFR Security:72 mentions PII purging from WAL)

---

## Summary Table

| ADR | Status | Recommendation |
|-----|--------|----------------|
| ADR-0001 | Keep | Add explicit NFR/C4 cross-references |
| ADR-0002 | Keep | Clarify which containers are replicated vs. standby |
| ADR-0003 | Keep | Good as-is |
| ADR-0004 | Revise | Add sections on WebSocket support, JWT forwarding, OIDC callback handling |
| **New** | — | Consider ADR for session/auth handling during CAD Server failover |
| **New** | — | Consider ADR for WAL format, replay, and PII purging strategy |

---

## Conclusion

The existing ADRs are reasonable choices with no better alternatives apparent. The main improvements would be:
1. Filling gaps around failover behavior
2. Addressing WebSocket/auth handling through the proxy
3. Adding cross-references to the NFRs and C4 specs they support

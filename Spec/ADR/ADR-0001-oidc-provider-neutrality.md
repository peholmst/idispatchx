# ADR-0001: OIDC Provider Neutrality

## Status
Accepted

## Context

iDispatchX uses OpenID Connect (OIDC) for authentication and identity management.

During development, a concrete OIDC provider is required to enable local testing,
user management, and integration testing. However, production deployments may use
different OIDC providers depending on organizational or regulatory requirements.

Locking iDispatchX to a specific OIDC provider would limit deployability and adoption.

## Decision

Keycloak is used as the OIDC provider for development and testing.

The system must not depend on Keycloak-specific behavior, APIs, or data models.
Production deployments must work with any standards-compliant OIDC provider.

Application components must rely solely on:
* OIDC standard flows
* JWT access tokens
* Standard claims and discovery mechanisms

Any required user metadata not reliably available in tokens (e.g. display name)
must be cached or managed internally by iDispatchX.

## Consequences

* Development is simplified by using a well-supported local OIDC provider
* The system remains portable across OIDC providers
* Additional internal handling may be required for user metadata
* Provider-specific extensions are explicitly avoided

## Cross-References

* [C4 Context](../C4/Context.md) — OIDC provider as external system (line 48)
* [NFR Security](../NonFunctionalRequirements/Security.md) — Centralized identity provider requirement
* [NFR Maintainability](../NonFunctionalRequirements/Maintainability.md) — 15-year lifespan goal (vendor lock-in avoidance)

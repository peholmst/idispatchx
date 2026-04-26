# Map View Reset Fix — Implementation Plan (Issue #48)

## References

- [Issue #48](https://github.com/peholmst/iDispatchX/issues/48) — Bug: map view resets by itself after several minutes (secondary window)
- [AuthState.ts](../../Implementation/clients/dispatcher-client/src/auth/AuthState.ts) — Emits `AuthChangedEvent` on every token refresh
- [AppShell.ts](../../Implementation/clients/dispatcher-client/src/ui/AppShell.ts) — Destroys and recreates windows on every `auth-changed` event

## Overview

When the OIDC access token is refreshed, `AuthState` emits an `AuthChangedEvent` with status
`{ kind: 'authenticated', tokenSet }`. `AppShell.#onStatusChanged` always calls `#renderStatus`,
which removes the existing `SecondaryWindow` from the DOM and creates a new one — resetting the
OpenLayers view to its initial centre, zoom, and base layer.

The `SecondaryWindow` (and `PrimaryWindow`) already hold a live reference to `AuthState` and call
`authState.getAccessToken()` on every tile request, so they do not need to be recreated when the
token rotates.

| Phase | Description | Tasks |
|-------|-------------|-------|
| 1 | Fix: skip re-render on token refresh | 1 |
| 2 | Regression test | 1 |

---

## Phase 1 — Fix

### Task 1.1 — Guard against authenticated → authenticated re-render in AppShell

**Status:** Not Started

**Problem:** `AppShell.#onStatusChanged` calls `#renderStatus` unconditionally. When the token is
refreshed, the status kind stays `authenticated`, but the entire window component is destroyed and
rebuilt, resetting all UI state including the map view.

**Changes to `Implementation/clients/dispatcher-client/src/ui/AppShell.ts`:**

- Add a private field `#renderedStatusKind: AuthStatus['kind'] | null = null` to track what
  was last rendered.
- In `#renderStatus`, set `this.#renderedStatusKind = status.kind` before returning.
- In `#onStatusChanged`, before calling `#renderStatus`, check:
  if `status.kind === 'authenticated'` **and** `this.#renderedStatusKind === 'authenticated'`,
  skip the `#renderStatus` call (session-manager start and channel open are still performed).

**Acceptance criteria:**
- An `AuthChangedEvent` with `kind: 'authenticated'` emitted while the secondary window is already
  displayed does **not** cause the window to be removed and recreated.
- An `AuthChangedEvent` with `kind: 'authenticated'` emitted when no window is yet shown (initial
  login) still renders the window.
- Transitioning from `expired` to `authenticated` (re-login) still renders the window.

**Dependencies:** None.

---

## Phase 2 — Regression Test

### Task 2.1 — Unit test for AppShell re-render guard

**Status:** Not Started

**Problem:** There is no automated test verifying that token refresh does not cause the window to
be recreated.

**Changes to `Implementation/clients/dispatcher-client/src/`:**

- Add a unit test in `AppShell.test.ts` (create if absent) that:
  1. Mounts `AppShell` in test mode.
  2. Fires a first `AuthChangedEvent` with `kind: 'authenticated'`.
  3. Captures a reference to the rendered window element.
  4. Fires a second `AuthChangedEvent` with `kind: 'authenticated'` (simulating token refresh).
  5. Asserts that the window element reference is still the same object (not replaced).

**Acceptance criteria:**
- Test passes under `vitest`.
- `npm test` (or `vitest run`) exits zero.

**Dependencies:** Task 1.1.

---

## Execution Notes

- Both tasks are sequential (test depends on the fix).
- The fix is a one-file, minimal change — no spec updates are required because the spec does not
  prescribe when `AppShell` re-renders its content; that is an implementation detail.

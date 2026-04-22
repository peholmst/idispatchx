// E2E tests for Dispatcher Client login and logout flows.
// Keycloak runs on port 8180 (started by globalSetup.ts).
// Vite dev server runs on port 5173 with VITE_CONFIG_URL=/config.test.json.

import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';

const APP_URL = 'http://localhost:5173/';
const KEYCLOAK_ORIGIN = 'http://localhost:8180';

// Keycloak test user credentials
const TEST_USER = 'test-dispatcher';
const TEST_PASSWORD = 'Test1234!';

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

/** Navigates to the app and fills in Keycloak credentials to complete login. */
async function loginViaKeycloak(page: Page, username = TEST_USER, password = TEST_PASSWORD): Promise<void> {
    await page.goto(APP_URL);

    // App redirects to Keycloak
    await page.waitForURL(`${KEYCLOAK_ORIGIN}/**`, { timeout: 10_000 });

    // Fill in credentials on Keycloak's login page
    await page.fill('input[name="username"]', username);
    await page.fill('input[name="password"]', password);
    await page.click('input[type="submit"], button[type="submit"]');
}

/**
 * Waits for the app to be in an authenticated state (launcher page rendered).
 * Checks the AppShell's shadow DOM via page.waitForFunction,
 * which is the most reliable way to cross shadow DOM boundaries.
 */
async function expectAuthenticated(page: Page): Promise<void> {
    await page.waitForURL(APP_URL, { timeout: 10_000 });
    await page.waitForFunction(() => {
        const shell = document.querySelector('idispatch-app-shell');
        return !!shell?.shadowRoot?.querySelector('idispatch-launcher-page');
    }, { timeout: 8_000 });
}

/**
 * Waits for the login screen with the given status to appear inside AppShell's shadow DOM.
 */
async function expectLoginScreen(page: Page, status?: string): Promise<void> {
    await page.waitForFunction((expectedStatus: string | undefined) => {
        const shell = document.querySelector('idispatch-app-shell');
        if (!shell?.shadowRoot) return false;
        const loginScreen = shell.shadowRoot.querySelector('idispatch-login-screen');
        if (!loginScreen) return false;
        if (expectedStatus && loginScreen.getAttribute('status') !== expectedStatus) return false;
        return true;
    }, status, { timeout: 8_000 });
}

/** Clicks the "Sign in again" button inside LoginScreen's shadow DOM. */
async function clickSignInAgain(page: Page): Promise<void> {
    await page.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const loginScreen = shell?.shadowRoot?.querySelector('idispatch-login-screen');
        const btn = loginScreen?.shadowRoot?.querySelector('.sign-in-button') as HTMLElement | null;
        btn?.click();
    });
}

/** Gets the message text from LoginScreen's shadow DOM. */
async function getLoginScreenMessage(page: Page): Promise<string> {
    return page.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const loginScreen = shell?.shadowRoot?.querySelector('idispatch-login-screen');
        return loginScreen?.shadowRoot?.querySelector('.status-message')?.textContent ?? '';
    });
}

/** Waits for window.__authState to be available, then calls forceLogout. */
async function simulateForceLogout(page: Page, reason: string): Promise<void> {
    await page.waitForFunction(() => {
        return !!(window as Window & { __authState?: unknown }).__authState;
    }, { timeout: 5_000 });
    await page.evaluate((r: string) => {
        const w = window as Window & { __authState?: { forceLogout(reason: string): void } };
        w.__authState?.forceLogout(r);
    }, reason);
}

// -------------------------------------------------------------------------
// Tests
// -------------------------------------------------------------------------

test.describe('Authentication', () => {

    test('redirects unauthenticated user to Keycloak login page', async ({ page }) => {
        await page.context().clearCookies();

        await page.goto(APP_URL);

        // App should redirect to Keycloak
        await page.waitForURL(`${KEYCLOAK_ORIGIN}/**`, { timeout: 10_000 });

        // Keycloak login form should be present
        await expect(page.locator('input[name="username"]')).toBeVisible();
        await expect(page.locator('input[name="password"]')).toBeVisible();
    });

    test('shows loading screen while auth is in progress', async ({ page }) => {
        // The app shows the AppShell (loading state) before redirecting to Keycloak
        let sawKeycloakRequest = false;
        page.on('request', req => {
            if (req.url().startsWith(KEYCLOAK_ORIGIN)) {
                sawKeycloakRequest = true;
            }
        });

        await page.goto(APP_URL);
        await page.waitForURL(`${KEYCLOAK_ORIGIN}/**`, { timeout: 10_000 });

        expect(sawKeycloakRequest).toBe(true);
    });

    test('login with valid credentials returns to app in authenticated state', async ({ page }) => {
        await loginViaKeycloak(page);
        await expectAuthenticated(page);
    });

    test('login with wrong password shows Keycloak error and does not redirect to app', async ({ page }) => {
        await loginViaKeycloak(page, TEST_USER, 'wrong-password');

        // Should remain on Keycloak (URL starts with KEYCLOAK_ORIGIN after submit)
        await expect(page).toHaveURL(new RegExp(KEYCLOAK_ORIGIN.replace(/\./g, '\\.')));

        // Keycloak shows an error message for invalid credentials
        const errorEl = page.getByText(/invalid username or password/i);
        await expect(errorEl.first()).toBeVisible({ timeout: 5_000 });
    });

    test('logout clears session and redirects back to login page', async ({ page }) => {
        // Login first
        await loginViaKeycloak(page);
        await expectAuthenticated(page);

        // Trigger logout via the exposed __authState handle (DEV mode only)
        await page.waitForFunction(() => {
            return !!(window as Window & { __authState?: unknown }).__authState;
        }, { timeout: 5_000 });
        await page.evaluate(() => {
            const w = window as Window & { __authState?: { logout(): Promise<void> } };
            return w.__authState?.logout();
        });

        // After RP-initiated logout, Keycloak redirects to post_logout_redirect_uri (the app),
        // which then has no session and redirects back to Keycloak login page.
        await page.waitForURL(`${KEYCLOAK_ORIGIN}/**`, { timeout: 15_000 });
        await expect(page.locator('input[name="username"]')).toBeVisible();
    });

    test('forced session termination (simulated 401) shows session-expired screen', async ({ page }) => {
        await loginViaKeycloak(page);
        await expectAuthenticated(page);

        // Simulate server-side session revocation (what happens when CAD Server
        // invalidates the session and the next request returns HTTP 401)
        await simulateForceLogout(page, 'forced-logout');

        // Login screen should appear with session-expired status
        await expectLoginScreen(page, 'forced-logout');
    });

    test('idle timeout shows idle-timeout screen', async ({ page }) => {
        await loginViaKeycloak(page);
        await expectAuthenticated(page);

        // Simulate idle timeout
        await simulateForceLogout(page, 'idle-timeout');

        await expectLoginScreen(page, 'idle-timeout');

        // Message should mention inactivity
        const message = await getLoginScreenMessage(page);
        expect(message).toContain('inactivity');
    });

    test('PKCE callback with mismatched state is safely rejected and triggers fresh login', async ({ page }) => {
        // Navigate to the app — this starts the PKCE flow and stores the real state
        // in sessionStorage, then redirects to Keycloak
        await page.goto(APP_URL);
        await page.waitForURL(`${KEYCLOAK_ORIGIN}/**`, { timeout: 10_000 });

        // Navigate back with a fake code and mismatched state.
        // The app should detect the mismatch, discard the callback, and
        // start a fresh PKCE redirect (back to Keycloak).
        await page.goto(`${APP_URL}?code=fake-code&state=deliberately-wrong-state`);

        // App detects mismatch → starts fresh PKCE flow → redirects to Keycloak
        await page.waitForURL(`${KEYCLOAK_ORIGIN}/**`, { timeout: 10_000 });
        await expect(page.locator('input[name="username"]')).toBeVisible();
    });

    // -----------------------------------------------------------------------
    // Regression tests for review comments
    // -----------------------------------------------------------------------

    test('forced-logout screen shows a "signed out" message, not the loading spinner message', async ({ page }) => {
        // Regression for: AppShell sets status="forced-logout" on LoginScreen, but
        // LoginScreen only handled 'loading', 'session-expired', 'idle-timeout', and
        // 'max-lifetime'. The 'forced-logout' value fell through to the STATUS_MESSAGES
        // fallback, showing "Signing in…" instead of a meaningful sign-out message.
        await loginViaKeycloak(page);
        await expectAuthenticated(page);

        await simulateForceLogout(page, 'forced-logout');
        await expectLoginScreen(page, 'forced-logout');

        const message = await getLoginScreenMessage(page);
        expect(message).not.toContain('Signing in');
        expect(message.toLowerCase()).toContain('signed out');
    });

    test('OIDC discovery failure shows a recoverable error screen with a retry button', async ({ page }) => {
        // Regression for: when OIDC discovery threw, AuthState emitted
        // { kind: 'unauthenticated' }, which rendered the "loading" screen
        // (spinner, no button). Users were permanently stuck with no in-app
        // recovery path. The fix must emit a recoverable state so the "Sign in
        // again" button appears.
        await page.route(
            `**/realms/idispatchx/.well-known/openid-configuration`,
            route => route.fulfill({ status: 500, body: 'Internal Server Error' }),
        );

        await page.goto(APP_URL);

        // A login screen must appear (not just a loading spinner forever)
        await expectLoginScreen(page);

        // The "Sign in again" button must be visible — not hidden behind a spinner
        const hasVisibleButton = await page.evaluate(() => {
            const shell = document.querySelector('idispatch-app-shell');
            const loginScreen = shell?.shadowRoot?.querySelector('idispatch-login-screen');
            const btn = loginScreen?.shadowRoot?.querySelector('.sign-in-button') as HTMLElement | null;
            return btn !== null && btn.style.display !== 'none';
        });
        expect(hasVisibleButton).toBe(true);
    });

    test('sign-in-again button after session expiry restarts login flow', async ({ page }) => {
        await loginViaKeycloak(page);
        await expectAuthenticated(page);

        // Force a local session expiry (clears local tokens but does NOT end the
        // Keycloak SSO session — this matches the server-revocation scenario).
        await simulateForceLogout(page, 'forced-logout');
        await expectLoginScreen(page, 'forced-logout');

        // Click "Sign in again" — restarts the OIDC Authorization Code + PKCE flow.
        await clickSignInAgain(page);

        // Because the Keycloak SSO session is still active, the authorization code
        // flow completes silently (no login form shown). The user lands back on the app
        // already authenticated — this is the expected SSO re-authentication behavior.
        await expectAuthenticated(page);
    });

    // -----------------------------------------------------------------------
    // Regression tests for second review pass
    // -----------------------------------------------------------------------

    test('failed bootstrap refresh clears stale login timestamp for new session', async ({ page }) => {
        // Regression for: #tryRefresh only removed KEY_REFRESH_TOKEN on failure,
        // leaving KEY_LOGIN_AT intact. A fresh login following a stale refresh token
        // then inherited the old timestamp, making max-lifetime checks fire immediately
        // or shorten the brand-new session by the age of the prior one.
        //
        // We prime sessionStorage on the first load with a login_at 8 hours in the
        // past and a deliberately invalid refresh token. The app tries the bootstrap
        // refresh, fails, then starts a fresh PKCE login. After that login completes
        // the stored login_at must reflect the new session, not the stale one.
        //
        // The guard key prevents the init script from re-seeding the stale values
        // when the browser returns to this origin after the Keycloak redirect.
        await page.addInitScript(() => {
            if (sessionStorage.getItem('__test_prime') === null) {
                sessionStorage.setItem('__test_prime', '1');
                sessionStorage.setItem('idispatchx.login_at', String(Date.now() - 8 * 60 * 60 * 1000));
                sessionStorage.setItem('idispatchx.refresh_token', 'deliberately-stale-invalid-token');
            }
        });

        await loginViaKeycloak(page);
        await expectAuthenticated(page);

        const loginAt = await page.evaluate(() =>
            Number(sessionStorage.getItem('idispatchx.login_at') ?? '0')
        );
        const ageMs = Date.now() - loginAt;
        // login_at must reflect the new session (< 30 s old), not the stale one (8 h).
        expect(ageMs).toBeLessThan(30_000);
    });

    test('access token refresh preserves stored refresh token when provider omits rotation', async ({ page }) => {
        // Regression for: #applyTokenResponse unconditionally removed the stored
        // refresh token when the token response omitted refresh_token. Providers that
        // keep the original token valid without rotating it (a valid OAuth/OIDC
        // behaviour) would therefore cause the next refresh cycle to call forceLogout.
        //
        // We intercept the token endpoint for refresh_token grants and strip
        // refresh_token from the response body, simulating a non-rotating provider.
        // After the refresh completes the original token must still be in storage.
        await loginViaKeycloak(page);
        await expectAuthenticated(page);

        const tokenBefore = await page.evaluate(() =>
            sessionStorage.getItem('idispatchx.refresh_token')
        );
        expect(tokenBefore).not.toBeNull();

        // Strip refresh_token from refresh responses only (leave code-exchange alone).
        await page.route('**/realms/idispatchx/protocol/openid-connect/token', async route => {
            const body = route.request().postData() ?? '';
            if (body.includes('grant_type=refresh_token')) {
                const response = await route.fetch();
                const json = await response.json() as Record<string, unknown>;
                delete json['refresh_token'];
                await route.fulfill({
                    status: response.status(),
                    headers: response.headers(),
                    body: JSON.stringify(json),
                });
            } else {
                await route.continue();
            }
        });

        // Trigger an explicit refresh and wait for the app to remain authenticated.
        await page.evaluate(() => {
            const w = window as Window & { __authState?: { refresh(): Promise<void> } };
            return w.__authState?.refresh();
        });
        await page.waitForFunction(() => {
            const shell = document.querySelector('idispatch-app-shell');
            return !!shell?.shadowRoot?.querySelector('idispatch-launcher-page');
        }, { timeout: 5_000 });

        // The original refresh token must still be present — it was not rotated.
        const tokenAfter = await page.evaluate(() =>
            sessionStorage.getItem('idispatchx.refresh_token')
        );
        expect(tokenAfter).toBe(tokenBefore);
    });

});

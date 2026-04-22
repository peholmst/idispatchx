// E2E tests for the Launcher Page and multi-window management.
// Keycloak runs on port 8180 (started by globalSetup.ts).
// Vite dev server runs on port 5173.

import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';

const APP_URL = 'http://localhost:5173/';
const KEYCLOAK_ORIGIN = 'http://localhost:8180';

const TEST_USER = 'test-dispatcher';
const TEST_PASSWORD = 'Test1234!';

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

async function loginViaKeycloak(page: Page): Promise<void> {
    await page.goto(APP_URL);
    await page.waitForURL(`${KEYCLOAK_ORIGIN}/**`, { timeout: 10_000 });
    await page.fill('input[name="username"]', TEST_USER);
    await page.fill('input[name="password"]', TEST_PASSWORD);
    await page.click('input[type="submit"], button[type="submit"]');
}

async function expectLauncherPage(page: Page): Promise<void> {
    await page.waitForURL(APP_URL, { timeout: 10_000 });
    await page.waitForFunction(() => {
        const shell = document.querySelector('idispatch-app-shell');
        return !!shell?.shadowRoot?.querySelector('idispatch-launcher-page');
    }, { timeout: 8_000 });
}

/** Returns the launcher page element from AppShell's shadow DOM. */
async function getLauncherShadow(page: Page): Promise<{ shadow: ShadowRoot | null }> {
    return page.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const launcher = shell?.shadowRoot?.querySelector('idispatch-launcher-page');
        return { shadow: launcher?.shadowRoot ?? null };
    });
}

/** Clicks a button inside the launcher's shadow DOM by its class name. */
async function clickLauncherBtn(page: Page, className: string): Promise<void> {
    await page.evaluate((cls: string) => {
        const shell = document.querySelector('idispatch-app-shell');
        const launcher = shell?.shadowRoot?.querySelector('idispatch-launcher-page');
        const btn = launcher?.shadowRoot?.querySelector(`.${cls}`) as HTMLElement | null;
        btn?.click();
    }, className);
}

// -------------------------------------------------------------------------
// Tests
// -------------------------------------------------------------------------

test.describe('Launcher Page', () => {

    test('renders launcher card with expected buttons after login', async ({ page }) => {
        // No locale is set — English default is active, so English labels are expected.
        // If this test is changed to run under a different locale, the expected strings
        // must be updated to match the corresponding translation table in src/i18n/index.ts.
        await page.context().clearCookies();
        await page.evaluate(() => localStorage.removeItem('idispatch:locale'));

        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        // Both window-open buttons are present in the launcher's shadow DOM
        const buttons = await page.evaluate(() => {
            const shell = document.querySelector('idispatch-app-shell');
            const launcher = shell?.shadowRoot?.querySelector('idispatch-launcher-page');
            const btns = Array.from(launcher?.shadowRoot?.querySelectorAll('button') ?? []);
            return btns.map(b => b.textContent?.trim());
        });

        expect(buttons).toContain('Open Primary Window');
        expect(buttons).toContain('Open Secondary Window');
        expect(buttons).toContain('Sign Out');
        expect(buttons).toContain('Account Management');
    });

    test('launcher shows current username', async ({ page }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const username = await page.evaluate(() => {
            const shell = document.querySelector('idispatch-app-shell');
            const launcher = shell?.shadowRoot?.querySelector('idispatch-launcher-page');
            return launcher?.shadowRoot?.querySelector('.launcher-username')?.textContent?.trim();
        });

        expect(username).toBe(TEST_USER);
    });

    test('opening primary window opens a new page at ?window=primary', async ({ page, context }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const [newPage] = await Promise.all([
            context.waitForEvent('page', { timeout: 10_000 }),
            clickLauncherBtn(page, 'open-primary-btn'),
        ]);

        await newPage.waitForLoadState('domcontentloaded');
        expect(newPage.url()).toContain('window=primary');

        await newPage.close();
    });

    test('opening secondary window opens a new page at ?window=secondary', async ({ page, context }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const [newPage] = await Promise.all([
            context.waitForEvent('page', { timeout: 10_000 }),
            clickLauncherBtn(page, 'open-secondary-btn'),
        ]);

        await newPage.waitForLoadState('domcontentloaded');
        expect(newPage.url()).toContain('window=secondary');

        await newPage.close();
    });

    test('clicking Open Primary Window a second time does not open a second window', async ({ page, context }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        // Open once
        const [firstPage] = await Promise.all([
            context.waitForEvent('page', { timeout: 10_000 }),
            clickLauncherBtn(page, 'open-primary-btn'),
        ]);
        await firstPage.waitForLoadState('domcontentloaded');

        const pageCountBefore = context.pages().length;

        // Click again — should focus the existing window, not open a new one
        await clickLauncherBtn(page, 'open-primary-btn');

        // Allow a moment for any navigation or window.open to execute
        await page.waitForTimeout(500);

        expect(context.pages().length).toBe(pageCountBefore);

        await firstPage.close();
    });

    test('after closing the primary window, opening it again succeeds', async ({ page, context }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        // Open and close
        const [firstPage] = await Promise.all([
            context.waitForEvent('page', { timeout: 10_000 }),
            clickLauncherBtn(page, 'open-primary-btn'),
        ]);
        await firstPage.waitForLoadState('domcontentloaded');
        await firstPage.close();

        // Wait for the poll interval to detect the closed window (>1 s)
        await page.waitForTimeout(1500);

        // Open again
        const [secondPage] = await Promise.all([
            context.waitForEvent('page', { timeout: 10_000 }),
            clickLauncherBtn(page, 'open-primary-btn'),
        ]);
        await secondPage.waitForLoadState('domcontentloaded');
        expect(secondPage.url()).toContain('window=primary');

        await secondPage.close();
    });

    test('beforeunload guard fires when a dispatcher window is open', async ({ page, context }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        // Open a primary window so the guard is armed
        const [dispatcherPage] = await Promise.all([
            context.waitForEvent('page', { timeout: 10_000 }),
            clickLauncherBtn(page, 'open-primary-btn'),
        ]);
        await dispatcherPage.waitForLoadState('domcontentloaded');

        // Dispatch a cancelable beforeunload event on the launcher and check
        // that the launcher's handler sets defaultPrevented = true.
        const wasCancelled = await page.evaluate(() => {
            const event = new Event('beforeunload', { cancelable: true, bubbles: false });
            window.dispatchEvent(event);
            return event.defaultPrevented;
        });

        expect(wasCancelled).toBe(true);

        await dispatcherPage.close();
    });

    test('sign-out propagates to open dispatcher windows via BroadcastChannel', async ({ page, context }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        // Open the primary window and wait for it to reach the authenticated state
        const [dispatcherPage] = await Promise.all([
            context.waitForEvent('page', { timeout: 10_000 }),
            clickLauncherBtn(page, 'open-primary-btn'),
        ]);
        await dispatcherPage.waitForFunction(() => {
            const shell = document.querySelector('idispatch-app-shell');
            return !!shell?.shadowRoot?.querySelector('idispatch-primary-window');
        }, { timeout: 20_000 });

        // Trigger sign-out from the launcher
        await clickLauncherBtn(page, 'launcher-btn-signout');

        // The dispatcher window should immediately show the forced-logout screen
        // without waiting for a token expiry or a 401 from the server.
        await dispatcherPage.waitForFunction(() => {
            const shell = document.querySelector('idispatch-app-shell');
            const loginScreen = shell?.shadowRoot?.querySelector('idispatch-login-screen');
            return loginScreen?.getAttribute('status') === 'forced-logout';
        }, { timeout: 8_000 });
    });

    test('beforeunload guard is removed after all dispatcher windows are closed', async ({ page, context }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const [dispatcherPage] = await Promise.all([
            context.waitForEvent('page', { timeout: 10_000 }),
            clickLauncherBtn(page, 'open-primary-btn'),
        ]);
        await dispatcherPage.waitForLoadState('domcontentloaded');
        await dispatcherPage.close();

        // Wait for the poll interval to detect closure and remove the guard
        await page.waitForTimeout(1500);

        const wasCancelled = await page.evaluate(() => {
            const event = new Event('beforeunload', { cancelable: true, bubbles: false });
            window.dispatchEvent(event);
            return event.defaultPrevented;
        });

        expect(wasCancelled).toBe(false);
    });

});

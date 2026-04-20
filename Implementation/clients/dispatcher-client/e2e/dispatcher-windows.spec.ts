// E2E tests for the primary and secondary dispatcher window structure.
// Tests open windows via the launcher, then assert header and footer content
// in the new page context.

import { expect, test } from '@playwright/test';
import type { Page, BrowserContext } from '@playwright/test';

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

/** Opens a dispatcher window via the launcher and waits until it is authenticated. */
async function openDispatcherWindow(
    launcherPage: Page,
    context: BrowserContext,
    type: 'primary' | 'secondary',
): Promise<Page> {
    const btnClass = type === 'primary' ? 'open-primary-btn' : 'open-secondary-btn';
    const windowTag = type === 'primary' ? 'idispatch-primary-window' : 'idispatch-secondary-window';

    const [newPage] = await Promise.all([
        context.waitForEvent('page', { timeout: 10_000 }),
        launcherPage.evaluate((cls: string) => {
            const shell = document.querySelector('idispatch-app-shell');
            const launcher = shell?.shadowRoot?.querySelector('idispatch-launcher-page');
            const btn = launcher?.shadowRoot?.querySelector(`.${cls}`) as HTMLElement | null;
            btn?.click();
        }, btnClass),
    ]);

    // Wait for the dispatcher window to fully render (auth + component mount)
    await newPage.waitForFunction((tag: string) => {
        const shell = document.querySelector('idispatch-app-shell');
        return !!shell?.shadowRoot?.querySelector(tag);
    }, windowTag, { timeout: 20_000 });

    return newPage;
}

/** Reads text from an element inside the window's deeply nested shadow DOM. */
async function getWindowShadowText(
    dispatcherPage: Page,
    windowTag: string,
    innerTag: string,
    selector: string,
): Promise<string | null> {
    return dispatcherPage.evaluate(
        ([wTag, iTag, sel]: string[]) => {
            const shell = document.querySelector('idispatch-app-shell');
            const win = shell?.shadowRoot?.querySelector(wTag);
            const inner = win?.shadowRoot?.querySelector(iTag);
            return inner?.shadowRoot?.querySelector(sel)?.textContent?.trim() ?? null;
        },
        [windowTag, innerTag, selector],
    );
}

// -------------------------------------------------------------------------
// Tests
// -------------------------------------------------------------------------

test.describe('Primary Window', () => {

    test('header shows the iD logo', async ({ page, context }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const primaryPage = await openDispatcherWindow(page, context, 'primary');

        const logoText = await getWindowShadowText(
            primaryPage, 'idispatch-primary-window', 'idispatch-window-header', '.brand-logo',
        );
        expect(logoText).toBe('iD');

        await primaryPage.close();
    });

    test('header clock shows date/time digits', async ({ page, context }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const primaryPage = await openDispatcherWindow(page, context, 'primary');

        const clockText = await getWindowShadowText(
            primaryPage, 'idispatch-primary-window', 'idispatch-window-header', '.header-clock',
        );
        expect(clockText).toBeTruthy();
        // Should contain digits (date/time)
        expect(clockText).toMatch(/\d/);

        await primaryPage.close();
    });

    test('header clock updates every second', async ({ page, context }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const primaryPage = await openDispatcherWindow(page, context, 'primary');

        const getClockText = (): Promise<string | null> =>
            getWindowShadowText(primaryPage, 'idispatch-primary-window', 'idispatch-window-header', '.header-clock');

        const before = await getClockText();
        await primaryPage.waitForTimeout(1500);
        const after = await getClockText();

        expect(before).not.toBeNull();
        expect(after).not.toBeNull();
        expect(after).not.toBe(before);

        await primaryPage.close();
    });

    test('header contains New Call and New Incident buttons', async ({ page, context }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const primaryPage = await openDispatcherWindow(page, context, 'primary');

        const buttonTexts = await primaryPage.evaluate(() => {
            const shell = document.querySelector('idispatch-app-shell');
            const win = shell?.shadowRoot?.querySelector('idispatch-primary-window');
            const header = win?.shadowRoot?.querySelector('idispatch-window-header');
            return Array.from(header?.shadowRoot?.querySelectorAll('button') ?? [])
                .map(b => b.textContent?.trim());
        });

        expect(buttonTexts).toContain('New Call');
        expect(buttonTexts).toContain('New Incident');

        await primaryPage.close();
    });

    test('footer shows current username', async ({ page, context }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const primaryPage = await openDispatcherWindow(page, context, 'primary');

        const footerUsername = await getWindowShadowText(
            primaryPage, 'idispatch-primary-window', 'idispatch-window-footer', '.footer-left',
        );
        expect(footerUsername).toBe(TEST_USER);

        await primaryPage.close();
    });

    test('footer shows Normal mode indicator', async ({ page, context }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const primaryPage = await openDispatcherWindow(page, context, 'primary');

        const modeText = await getWindowShadowText(
            primaryPage, 'idispatch-primary-window', 'idispatch-window-footer', '.footer-right',
        );
        expect(modeText).toBe('Normal');

        await primaryPage.close();
    });

});

test.describe('Secondary Window', () => {

    test('header shows the iD logo and clock', async ({ page, context }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const secondaryPage = await openDispatcherWindow(page, context, 'secondary');

        const logoText = await getWindowShadowText(
            secondaryPage, 'idispatch-secondary-window', 'idispatch-window-header', '.brand-logo',
        );
        expect(logoText).toBe('iD');

        const clockText = await getWindowShadowText(
            secondaryPage, 'idispatch-secondary-window', 'idispatch-window-header', '.header-clock',
        );
        expect(clockText).toMatch(/\d/);

        await secondaryPage.close();
    });

    test('header does NOT contain New Call or New Incident buttons', async ({ page, context }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const secondaryPage = await openDispatcherWindow(page, context, 'secondary');

        const buttonTexts = await secondaryPage.evaluate(() => {
            const shell = document.querySelector('idispatch-app-shell');
            const win = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
            const header = win?.shadowRoot?.querySelector('idispatch-window-header');
            return Array.from(header?.shadowRoot?.querySelectorAll('button') ?? [])
                .map(b => b.textContent?.trim());
        });

        expect(buttonTexts).not.toContain('New Call');
        expect(buttonTexts).not.toContain('New Incident');

        await secondaryPage.close();
    });

    test('footer shows current username and Normal mode', async ({ page, context }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const secondaryPage = await openDispatcherWindow(page, context, 'secondary');

        const footerUsername = await getWindowShadowText(
            secondaryPage, 'idispatch-secondary-window', 'idispatch-window-footer', '.footer-left',
        );
        expect(footerUsername).toBe(TEST_USER);

        const modeText = await getWindowShadowText(
            secondaryPage, 'idispatch-secondary-window', 'idispatch-window-footer', '.footer-right',
        );
        expect(modeText).toBe('Normal');

        await secondaryPage.close();
    });

});

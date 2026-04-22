// E2E tests for the i18n mechanism (locale selection, persistence, and
// translated UI strings across all affected components).
//
// Each test that depends on a specific locale sets localStorage directly
// before navigation so that the i18n module reads the correct value on load.

import { expect, test, type Page } from '@playwright/test';

const APP_URL = 'http://localhost:5173/';
const KEYCLOAK_ORIGIN = 'http://localhost:8180';
const TEST_USER = 'test-dispatcher';
const TEST_PASSWORD = 'Test1234!';
const LOCALE_KEY = 'idispatch:locale';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function setLocaleInStorage(page: Page, locale: string): Promise<void> {
    await page.addInitScript(
        ({ key, value }: { key: string; value: string }) => { localStorage.setItem(key, value); },
        { key: LOCALE_KEY, value: locale },
    );
}

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

/** Returns all button text values inside the launcher's shadow DOM. */
async function getLauncherButtonLabels(page: Page): Promise<(string | undefined)[]> {
    return page.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const launcher = shell?.shadowRoot?.querySelector('idispatch-launcher-page');
        return Array.from(launcher?.shadowRoot?.querySelectorAll('button') ?? [])
            .map(b => b.textContent?.trim());
    });
}

/** Returns the text of the footer mode indicator in the primary window. */
async function getPrimaryWindowFooterMode(page: Page): Promise<string> {
    return page.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const win = shell?.shadowRoot?.querySelector('idispatch-primary-window');
        const footer = win?.shadowRoot?.querySelector('idispatch-window-footer');
        return footer?.shadowRoot?.querySelector('.footer-right')?.textContent?.trim() ?? '';
    });
}

/** Returns the text labels of primary action buttons in the primary window header. */
async function getPrimaryWindowActionLabels(page: Page): Promise<(string | undefined)[]> {
    return page.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const win = shell?.shadowRoot?.querySelector('idispatch-primary-window');
        return Array.from(win?.shadowRoot?.querySelectorAll('.header-action-btn') ?? [])
            .map(b => b.textContent?.trim());
    });
}

async function openPrimaryWindow(page: Page): Promise<Page> {
    const [newPage] = await Promise.all([
        page.context().waitForEvent('page', { timeout: 10_000 }),
        page.evaluate(() => {
            const shell = document.querySelector('idispatch-app-shell');
            const launcher = shell?.shadowRoot?.querySelector('idispatch-launcher-page');
            const btn = launcher?.shadowRoot?.querySelector('.open-primary-btn') as HTMLElement | null;
            btn?.click();
        }),
    ]);
    await newPage.waitForFunction(() => {
        const shell = document.querySelector('idispatch-app-shell');
        return !!shell?.shadowRoot?.querySelector('idispatch-primary-window');
    }, { timeout: 20_000 });
    return newPage;
}

async function simulateForceLogout(page: Page, reason: string): Promise<void> {
    await page.waitForFunction(() => {
        return !!(window as Window & { __authState?: unknown }).__authState;
    }, { timeout: 5_000 });
    await page.evaluate((r: string) => {
        const w = window as Window & { __authState?: { forceLogout(reason: string): void } };
        w.__authState?.forceLogout(r);
    }, reason);
}

async function getLoginScreenTexts(page: Page): Promise<{ message: string; buttonLabel: string }> {
    return page.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const loginScreen = shell?.shadowRoot?.querySelector('idispatch-login-screen');
        const message = loginScreen?.shadowRoot?.querySelector('.status-message')?.textContent?.trim() ?? '';
        const buttonLabel = loginScreen?.shadowRoot?.querySelector('.sign-in-button')?.textContent?.trim() ?? '';
        return { message, buttonLabel };
    });
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test.describe('i18n — locale selection and UI string localisation', () => {

    test('2.1 — default locale is English when no locale is stored', async ({ page }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const storedLocale = await page.evaluate((key: string) => localStorage.getItem(key), LOCALE_KEY);
        // The module treats absence as 'en'; it does not write a default value back
        expect(storedLocale === null || storedLocale === 'en').toBe(true);

        const buttons = await getLauncherButtonLabels(page);
        expect(buttons).toContain('Open Primary Window');
        expect(buttons).toContain('Open Secondary Window');
        expect(buttons).toContain('Account Management');
        expect(buttons).toContain('Sign Out');
    });

    test('2.2 — unrecognised locale falls back to English', async ({ page }) => {
        await setLocaleInStorage(page, 'xx');
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const buttons = await getLauncherButtonLabels(page);
        expect(buttons).toContain('Open Primary Window');
        expect(buttons).toContain('Open Secondary Window');
        expect(buttons).toContain('Account Management');
        expect(buttons).toContain('Sign Out');
    });

    test('2.3 — Finnish locale renders Finnish labels on the launcher', async ({ page }) => {
        await setLocaleInStorage(page, 'fi');
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const buttons = await getLauncherButtonLabels(page);
        expect(buttons).toContain('Avaa ensisijainen ikkuna');
        expect(buttons).toContain('Avaa toissijainen ikkuna');
        expect(buttons).toContain('Tilin hallinta');
        expect(buttons).toContain('Kirjaudu ulos');

        // The stored locale must not have been overwritten
        const storedLocale = await page.evaluate((key: string) => localStorage.getItem(key), LOCALE_KEY);
        expect(storedLocale).toBe('fi');
    });

    test('2.3 — Finnish locale renders Finnish labels in the primary window', async ({ page }) => {
        await setLocaleInStorage(page, 'fi');
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const primaryPage = await openPrimaryWindow(page);

        const actionLabels = await getPrimaryWindowActionLabels(primaryPage);
        expect(actionLabels).toContain('Uusi puhelu');
        expect(actionLabels).toContain('Uusi tehtävä');

        const footerMode = await getPrimaryWindowFooterMode(primaryPage);
        expect(footerMode).toBe('Normaali');

        await primaryPage.close();
    });

    test('2.4 — Swedish locale renders Swedish labels on the launcher', async ({ page }) => {
        await setLocaleInStorage(page, 'sv');
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const buttons = await getLauncherButtonLabels(page);
        expect(buttons).toContain('Öppna primärt fönster');
        expect(buttons).toContain('Öppna sekundärt fönster');
        expect(buttons).toContain('Kontohantering');
        expect(buttons).toContain('Logga ut');
    });

    test('2.4 — Swedish locale renders Swedish labels in the primary window', async ({ page }) => {
        await setLocaleInStorage(page, 'sv');
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const primaryPage = await openPrimaryWindow(page);

        const actionLabels = await getPrimaryWindowActionLabels(primaryPage);
        expect(actionLabels).toContain('Nytt samtal');
        expect(actionLabels).toContain('Nytt uppdrag');

        const footerMode = await getPrimaryWindowFooterMode(primaryPage);
        expect(footerMode).toBe('Normal');

        await primaryPage.close();
    });

    test('2.5 — selected locale persists across a page reload', async ({ page }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        // Switch to Finnish via the language selector; this triggers a reload
        await page.evaluate(() => {
            const shell = document.querySelector('idispatch-app-shell');
            const launcher = shell?.shadowRoot?.querySelector('idispatch-launcher-page');
            const fiBtn = launcher?.shadowRoot?.querySelector<HTMLButtonElement>('.lang-btn[lang="fi"]');
            fiBtn?.click();
        });

        // Wait for the reload to settle back on the launcher
        await expectLauncherPage(page);

        // Finnish labels must be shown without any manual localStorage setup
        const buttons = await getLauncherButtonLabels(page);
        expect(buttons).toContain('Avaa ensisijainen ikkuna');

        // The locale must still be persisted
        const storedLocale = await page.evaluate((key: string) => localStorage.getItem(key), LOCALE_KEY);
        expect(storedLocale).toBe('fi');
    });

    test('2.6 — language selector renders with the active locale marked', async ({ page }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        const selectorState = await page.evaluate(() => {
            const shell = document.querySelector('idispatch-app-shell');
            const launcher = shell?.shadowRoot?.querySelector('idispatch-launcher-page');
            const shadow = launcher?.shadowRoot;
            if (!shadow) return null;

            const langBtns = Array.from(shadow.querySelectorAll<HTMLButtonElement>('.lang-btn'));
            return langBtns.map(btn => ({
                lang: btn.getAttribute('lang'),
                label: btn.textContent?.trim(),
                isActive: btn.classList.contains('lang-btn-active'),
                ariaCurrent: btn.getAttribute('aria-current'),
            }));
        });

        expect(selectorState).not.toBeNull();
        expect(selectorState).toHaveLength(3);

        const fi = selectorState!.find(b => b.lang === 'fi');
        const sv = selectorState!.find(b => b.lang === 'sv');
        const en = selectorState!.find(b => b.lang === 'en');

        expect(fi).toBeDefined();
        expect(sv).toBeDefined();
        expect(en).toBeDefined();

        // English is the default active locale
        expect(en!.isActive).toBe(true);
        expect(en!.ariaCurrent).toBe('true');

        // The other two must not be marked active
        expect(fi!.isActive).toBe(false);
        expect(sv!.isActive).toBe(false);
    });

    test('2.7 — clicking a language button triggers a reload and applies the new locale', async ({ page }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        // Click the Swedish button
        await page.evaluate(() => {
            const shell = document.querySelector('idispatch-app-shell');
            const launcher = shell?.shadowRoot?.querySelector('idispatch-launcher-page');
            const svBtn = launcher?.shadowRoot?.querySelector<HTMLButtonElement>('.lang-btn[lang="sv"]');
            svBtn?.click();
        });

        // Wait for the reload to settle
        await expectLauncherPage(page);

        // Swedish labels must be shown
        const buttons = await getLauncherButtonLabels(page);
        expect(buttons).toContain('Öppna primärt fönster');
        expect(buttons).toContain('Logga ut');
    });

    test('2.8 — login screen strings are localised (English default)', async ({ page }) => {
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        await simulateForceLogout(page, 'forced-logout');

        await page.waitForFunction(() => {
            const shell = document.querySelector('idispatch-app-shell');
            return shell?.shadowRoot?.querySelector('idispatch-login-screen') !== null;
        }, { timeout: 5_000 });

        const { message, buttonLabel } = await getLoginScreenTexts(page);
        expect(message.toLowerCase()).toContain('signed out');
        expect(buttonLabel).toBe('Sign in again');
    });

    test('2.8 — login screen strings are localised (Finnish)', async ({ page }) => {
        await setLocaleInStorage(page, 'fi');
        await loginViaKeycloak(page);
        await expectLauncherPage(page);

        await simulateForceLogout(page, 'forced-logout');

        await page.waitForFunction(() => {
            const shell = document.querySelector('idispatch-app-shell');
            return shell?.shadowRoot?.querySelector('idispatch-login-screen') !== null;
        }, { timeout: 5_000 });

        const { message, buttonLabel } = await getLoginScreenTexts(page);
        expect(message).toBe('Sinut on kirjattu ulos.');
        expect(buttonLabel).toBe('Kirjaudu uudelleen');
    });

});

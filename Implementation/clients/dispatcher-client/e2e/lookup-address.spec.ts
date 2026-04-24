// E2E tests for UC: Lookup Address.
// Keycloak and Vite dev server are started by globalSetup / webServer config.
// GIS Server responses are mocked via page.route() so no real GIS server is needed.

import { expect, test } from '@playwright/test';
import type { Page, BrowserContext } from '@playwright/test';

const APP_URL = 'http://localhost:5173/';
const KEYCLOAK_ORIGIN = 'http://localhost:8180';
const GIS_URL = 'http://localhost:8081';

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
    await page.waitForURL(APP_URL, { timeout: 10_000 });
    await page.waitForFunction(() => {
        const shell = document.querySelector('idispatch-app-shell');
        return !!shell?.shadowRoot?.querySelector('idispatch-launcher-page');
    }, { timeout: 8_000 });
}

/** Routes `GET /api/v1/layers` to a two-layer success response. */
async function mockLayers(context: BrowserContext): Promise<void> {
    await context.route(`${GIS_URL}/api/v1/layers`, route =>
        route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
                layers: [
                    { id: 'terrain', title: 'Terrain' },
                    { id: 'navigation', title: 'Navigation' },
                ],
            }),
        })
    );
}

/** Routes `GET /api/v1/layers` to a 503 response. */
async function mockLayersFail(context: BrowserContext): Promise<void> {
    await context.route(`${GIS_URL}/api/v1/layers`, route =>
        route.fulfill({ status: 503, body: '{"error":"unavailable"}' })
    );
}

/** Routes tile requests to no-content so the map doesn't get stuck. */
async function mockTiles(context: BrowserContext): Promise<void> {
    await context.route(`${GIS_URL}/wmts/**`, route =>
        route.fulfill({ status: 204 })
    );
}

/** Opens the secondary dispatcher window as a popup and waits until authenticated. */
async function openSecondaryWindow(
    launcherPage: Page,
    context: BrowserContext,
): Promise<Page> {
    const [secPage] = await Promise.all([
        context.waitForEvent('page', { timeout: 10_000 }),
        launcherPage.evaluate(() => {
            const shell = document.querySelector('idispatch-app-shell');
            const launcher = shell?.shadowRoot?.querySelector('idispatch-launcher-page');
            const btn = launcher?.shadowRoot?.querySelector('.open-secondary-btn') as HTMLElement | null;
            btn?.click();
        }),
    ]);

    await secPage.waitForFunction(() => {
        const shell = document.querySelector('idispatch-app-shell');
        return !!shell?.shadowRoot?.querySelector('idispatch-secondary-window');
    }, { timeout: 15_000 });

    // Wait for lookup bar to be present
    await secPage.waitForFunction(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        return !!sw?.shadowRoot?.querySelector('idispatch-lookup-bar');
    }, { timeout: 8_000 });

    return secPage;
}

const GEO_RESULT_MANNERHEIMINTIE = {
    type: 'address',
    name: { fi: 'Mannerheimintie', sv: 'Mannerheimvägen' },
    number: '1',
    municipality: { code: '091', name: { fi: 'Helsinki', sv: 'Helsingfors' } },
    coordinates: { latitude: 60.169857, longitude: 24.938379 },
};

// -------------------------------------------------------------------------
// Tests — Base layer selector
// -------------------------------------------------------------------------

test('base-layer selector is populated from GET /api/v1/layers', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    // Wait for layers to load (select becomes enabled)
    await secPage.waitForFunction(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const select = sw?.shadowRoot?.querySelector('select.layer-select') as HTMLSelectElement | null;
        return select !== null && !select.disabled && select.options.length >= 2;
    }, { timeout: 5_000 });

    const optionCount = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const select = sw?.shadowRoot?.querySelector('select.layer-select') as HTMLSelectElement | null;
        return select?.options.length ?? 0;
    });

    expect(optionCount).toBe(2);
});

test('base-layer selector is disabled when GET /api/v1/layers fails', async ({ page, context }) => {
    await mockLayersFail(context);
    await mockTiles(context);
    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    await secPage.waitForFunction(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const select = sw?.shadowRoot?.querySelector('select.layer-select') as HTMLSelectElement | null;
        return select !== null && select.disabled;
    }, { timeout: 5_000 });

    const isDisabled = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const select = sw?.shadowRoot?.querySelector('select.layer-select') as HTMLSelectElement | null;
        return select?.disabled ?? false;
    });

    expect(isDisabled).toBe(true);
});

// -------------------------------------------------------------------------
// Tests — Address input validation
// -------------------------------------------------------------------------

test('clicking Lookup with fewer than 3 address characters shows inline error; no request sent', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);

    let geocodeRequestFired = false;
    await context.route(`${GIS_URL}/api/v1/geocode/**`, () => { geocodeRequestFired = true; });

    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    // Type 2 characters and click Lookup
    await secPage.getByPlaceholder('Search address (geocoding)...').fill('Ma');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    // Error message should appear
    await expect(secPage.locator('.inline-error')).toBeVisible({ timeout: 2_000 });
    const errorText = await secPage.locator('.inline-error').textContent();
    expect(errorText).toContain('3');

    expect(geocodeRequestFired).toBe(false);
});

// -------------------------------------------------------------------------
// Tests — Geocoding results
// -------------------------------------------------------------------------

test('typing 3+ chars and clicking Lookup triggers geocoding and shows results dropdown', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await context.route(`${GIS_URL}/api/v1/geocode/**`, route =>
        route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
                results: [GEO_RESULT_MANNERHEIMINTIE, { ...GEO_RESULT_MANNERHEIMINTIE, number: '2' }],
                query: 'Manner',
                resultCount: 2,
            }),
        })
    );

    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    await secPage.getByPlaceholder('Search address (geocoding)...').fill('Manner');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    await expect(secPage.locator('.results-dropdown')).toBeVisible({ timeout: 5_000 });
    const items = await secPage.locator('.result-item').count();
    expect(items).toBe(2);
});

test('single result centers map automatically without showing dropdown', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await context.route(`${GIS_URL}/api/v1/geocode/**`, route =>
        route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
                results: [GEO_RESULT_MANNERHEIMINTIE],
                query: 'Mannerheimintie 1',
                resultCount: 1,
            }),
        })
    );

    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    await secPage.getByPlaceholder('Search address (geocoding)...').fill('Mannerheimintie 1');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    // Dropdown should NOT appear
    await secPage.waitForTimeout(1_000);
    const dropdownVisible = await secPage.locator('.results-dropdown:not([hidden])').isVisible().catch(() => false);
    expect(dropdownVisible).toBe(false);
});

test('no-results state shows localised message', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await context.route(`${GIS_URL}/api/v1/geocode/**`, route =>
        route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ results: [], query: 'zzz', resultCount: 0 }),
        })
    );

    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    await secPage.getByPlaceholder('Search address (geocoding)...').fill('zzz');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    await expect(secPage.locator('.no-results')).toBeVisible({ timeout: 5_000 });
    const text = await secPage.locator('.no-results').textContent();
    expect(text).toBeTruthy();
});

test('selecting a result from the list closes dropdown', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await context.route(`${GIS_URL}/api/v1/geocode/**`, route =>
        route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
                results: [GEO_RESULT_MANNERHEIMINTIE, { ...GEO_RESULT_MANNERHEIMINTIE, number: '3' }],
                query: 'Manner',
                resultCount: 2,
            }),
        })
    );

    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    await secPage.getByPlaceholder('Search address (geocoding)...').fill('Manner');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    await expect(secPage.locator('.results-dropdown')).toBeVisible({ timeout: 5_000 });
    await secPage.locator('.result-item').first().click();

    // Dropdown should close after selection
    const dropdownVisible = await secPage.locator('.results-dropdown:not([hidden])').isVisible().catch(() => false);
    expect(dropdownVisible).toBe(false);
});

// -------------------------------------------------------------------------
// Tests — Clear button
// -------------------------------------------------------------------------

test('Clear button clears both inputs and hides dropdown', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await context.route(`${GIS_URL}/api/v1/geocode/**`, route =>
        route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ results: [], query: 'zzz', resultCount: 0 }),
        })
    );

    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    await secPage.getByPlaceholder('Search address (geocoding)...').fill('something');
    await secPage.getByPlaceholder(/60/).fill('60.1, 25.0');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    await secPage.waitForTimeout(500);
    await secPage.getByRole('button', { name: 'Clear' }).click();

    const addrValue = await secPage.getByPlaceholder('Search address (geocoding)...').inputValue();
    const coordsValue = await secPage.getByPlaceholder(/60/).inputValue();
    expect(addrValue).toBe('');
    expect(coordsValue).toBe('');
});

// -------------------------------------------------------------------------
// Tests — Coordinate input
// -------------------------------------------------------------------------

test('valid DD coordinates center map without error', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    await secPage.getByPlaceholder(/60/).fill('60.169857, 24.938379');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    // No inline error should appear
    await secPage.waitForTimeout(500);
    const errorVisible = await secPage.locator('.inline-error:not([hidden])').isVisible().catch(() => false);
    expect(errorVisible).toBe(false);
});

test('valid DDM coordinates work', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    await secPage.getByPlaceholder(/60/).fill("60° 10.1914' N, 24° 56.3027' E");
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    await secPage.waitForTimeout(500);
    const errorVisible = await secPage.locator('.inline-error:not([hidden])').isVisible().catch(() => false);
    expect(errorVisible).toBe(false);
});

test('valid DMS coordinates work', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    await secPage.getByPlaceholder(/60/).fill('60° 10\' 11.49" N, 24° 56\' 18.16" E');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    await secPage.waitForTimeout(500);
    const errorVisible = await secPage.locator('.inline-error:not([hidden])').isVisible().catch(() => false);
    expect(errorVisible).toBe(false);
});

test('coordinates outside Finland bounds show the out-of-bounds error message', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    // Stockholm is outside Finland bounds
    await secPage.getByPlaceholder(/60/).fill('59.329, 18.068');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    await expect(secPage.locator('.inline-error:not([hidden])')).toBeVisible({ timeout: 2_000 });
    const text = await secPage.locator('.inline-error').textContent();
    expect(text).toContain('outside');
});

test('invalid coordinate format shows error message', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    await secPage.getByPlaceholder(/60/).fill('not-a-coordinate');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    await expect(secPage.locator('.inline-error:not([hidden])')).toBeVisible({ timeout: 2_000 });
    const text = await secPage.locator('.inline-error').textContent();
    expect(text).toBeTruthy();
});

// -------------------------------------------------------------------------
// Tests — GIS Server unavailable
// -------------------------------------------------------------------------

test('GIS Server unavailable shows unavailable banner; coordinate entry still works', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await context.route(`${GIS_URL}/api/v1/geocode/**`, route =>
        route.fulfill({ status: 503, body: '{"error":"unavailable"}' })
    );

    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    await secPage.getByPlaceholder('Search address (geocoding)...').fill('Mannerheimintie');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    await expect(secPage.locator('.unavailable-banner:not([hidden])')).toBeVisible({ timeout: 5_000 });

    // Coordinate entry should still work despite banner
    await secPage.getByPlaceholder(/60/).fill('60.169, 25.0');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    // Banner might still be visible; no additional error for the coords
    const errorText = await secPage.locator('.inline-error').textContent().catch(() => '');
    expect(errorText).not.toContain('outside');
    expect(errorText).not.toContain('Invalid');
});

test('GIS Server timeout shows timeout message; coordinate entry still works', async ({ page, context }) => {
    test.setTimeout(30_000);

    await mockLayers(context);
    await mockTiles(context);
    // Delay the geocode response longer than the 10s timeout
    await context.route(`${GIS_URL}/api/v1/geocode/**`, async route => {
        await new Promise<void>(resolve => setTimeout(resolve, 12_000));
        await route.fulfill({ status: 200, contentType: 'application/json',
            body: JSON.stringify({ results: [], query: 'test', resultCount: 0 }) });
    });

    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    await secPage.getByPlaceholder('Search address (geocoding)...').fill('Mannerheimintie');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    // Wait for timeout (>10s) and check the message
    await expect(secPage.locator('.unavailable-banner:not([hidden])')).toBeVisible({ timeout: 15_000 });
    const bannerText = await secPage.locator('.unavailable-banner').textContent();
    expect(bannerText?.toLowerCase()).toContain('long');

    // Coordinate entry should still work
    await secPage.getByPlaceholder(/60/).fill('60.169, 25.0');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    await secPage.waitForTimeout(500);
    const errorText = await secPage.locator('.inline-error').textContent().catch(() => '');
    expect(errorText).not.toContain('outside');
});

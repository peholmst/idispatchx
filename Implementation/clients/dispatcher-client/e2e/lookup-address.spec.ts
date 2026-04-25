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

// -------------------------------------------------------------------------
// Helper to access shadow DOM elements
// -------------------------------------------------------------------------

async function getSecondaryWindow(page: Page) {
    return page.evaluateHandle(() => {
        const shell = document.querySelector('idispatch-app-shell');
        return shell?.shadowRoot?.querySelector('idispatch-secondary-window') ?? null;
    });
}

async function getLookupBarHandle(page: Page) {
    return page.evaluateHandle(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        return (sw?.shadowRoot?.querySelector('idispatch-lookup-bar') ?? null) as (HTMLElement & { markerFeatureCount: number }) | null;
    });
}

// -------------------------------------------------------------------------
// Tests — issue #45, item 1: Font family
// -------------------------------------------------------------------------

test('toolbar and lookup bar use the design-system font (issue #45, item 1)', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    const fontFamily = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const lb = sw?.shadowRoot?.querySelector('idispatch-lookup-bar') as HTMLElement | null;
        return lb ? getComputedStyle(lb).fontFamily : '';
    });

    expect(fontFamily).toMatch(/Segoe UI|system-ui/i);
});

// -------------------------------------------------------------------------
// Tests — issue #45, item 2: Internationalised toolbar labels
// -------------------------------------------------------------------------

test('toolbar labels switch to Finnish when Finnish locale is active (issue #45, item 2)', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await loginViaKeycloak(page);

    // Set Finnish locale before opening secondary window (shared localStorage for same origin)
    await page.evaluate(() => localStorage.setItem('idispatch:locale', 'fi'));

    const secPage = await openSecondaryWindow(page, context);

    const labels = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const spans = sw?.shadowRoot?.querySelectorAll('.map-toolbar-label');
        return spans ? Array.from(spans).map(s => s.textContent ?? '') : [];
    });

    expect(labels).toContain('Pohjakartta:');
    expect(labels).toContain('Tasot:');

    // Reset locale for other tests
    await page.evaluate(() => localStorage.removeItem('idispatch:locale'));
});

// -------------------------------------------------------------------------
// Tests — issue #45, item 6: Street number in dropdown
// -------------------------------------------------------------------------

test('address result shows street number alongside name in dropdown (issue #45, item 6)', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await context.route(`${GIS_URL}/api/v1/geocode/**`, route =>
        route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
                results: [
                    { ...GEO_RESULT_MANNERHEIMINTIE, number: '5' },
                    { ...GEO_RESULT_MANNERHEIMINTIE, number: '7' },
                ],
                query: 'Mannerheimintie',
                resultCount: 2,
            }),
        })
    );

    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    await secPage.getByPlaceholder('Search address (geocoding)...').fill('Mannerheimintie');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    await expect(secPage.locator('.results-dropdown')).toBeVisible({ timeout: 5_000 });
    const firstResultText = await secPage.locator('.result-name').first().textContent();
    expect(firstResultText).toContain('Mannerheimintie');
    expect(firstResultText).toContain('5');
});

// -------------------------------------------------------------------------
// Tests — issue #45, item 7: Marker persistence
// -------------------------------------------------------------------------

test('marker persists after map pan (issue #45, item 7)', async ({ page, context }) => {
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

    // Select a result to place a marker
    await secPage.getByPlaceholder('Search address (geocoding)...').fill('Mannerheimintie 1');
    await secPage.getByRole('button', { name: 'Lookup' }).click();
    await secPage.waitForTimeout(800); // let animation settle

    // Verify marker is present
    const featureCountBefore = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const lb = sw?.shadowRoot?.querySelector('idispatch-lookup-bar') as any;
        return lb?.markerFeatureCount ?? -1;
    });
    expect(featureCountBefore).toBe(1);

    // Simulate a map pan using mouse drag
    const mapBounds = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const mapEl = sw?.shadowRoot?.querySelector('.ol-map-target');
        const rect = mapEl?.getBoundingClientRect();
        return rect ? { x: rect.x, y: rect.y, width: rect.width, height: rect.height } : null;
    });

    if (mapBounds) {
        const cx = mapBounds.x + mapBounds.width / 2;
        const cy = mapBounds.y + mapBounds.height / 2;
        await secPage.mouse.move(cx, cy);
        await secPage.mouse.down();
        await secPage.mouse.move(cx + 80, cy + 40);
        await secPage.mouse.up();
    }

    await secPage.waitForTimeout(500);

    const featureCountAfter = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const lb = sw?.shadowRoot?.querySelector('idispatch-lookup-bar') as any;
        return lb?.markerFeatureCount ?? -1;
    });
    expect(featureCountAfter).toBe(1);
});

test('marker persists after map zoom (issue #45, item 7)', async ({ page, context }) => {
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
    await secPage.waitForTimeout(800);

    // Zoom in using keyboard shortcut (+ key on map)
    const mapBounds = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const mapEl = sw?.shadowRoot?.querySelector('.ol-map-target');
        const rect = mapEl?.getBoundingClientRect();
        return rect ? { x: rect.x, y: rect.y, width: rect.width, height: rect.height } : null;
    });

    if (mapBounds) {
        await secPage.mouse.click(mapBounds.x + mapBounds.width / 2, mapBounds.y + mapBounds.height / 2);
        await secPage.keyboard.press('+');
        await secPage.waitForTimeout(400);
    }

    const featureCountAfter = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const lb = sw?.shadowRoot?.querySelector('idispatch-lookup-bar') as any;
        return lb?.markerFeatureCount ?? -1;
    });
    expect(featureCountAfter).toBe(1);
});

test('clicking Clear removes the marker (issue #45, item 7)', async ({ page, context }) => {
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
    await secPage.waitForTimeout(800);

    const featureCountBefore = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const lb = sw?.shadowRoot?.querySelector('idispatch-lookup-bar') as any;
        return lb?.markerFeatureCount ?? -1;
    });
    expect(featureCountBefore).toBe(1);

    await secPage.getByRole('button', { name: 'Clear' }).click();

    const featureCountAfter = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const lb = sw?.shadowRoot?.querySelector('idispatch-lookup-bar') as any;
        return lb?.markerFeatureCount ?? -1;
    });
    expect(featureCountAfter).toBe(0);
});

// -------------------------------------------------------------------------
// Tests — issue #45, item 8: Keyboard navigation
// -------------------------------------------------------------------------

test('ArrowDown highlights first result; Enter selects it (issue #45, item 8)', async ({ page, context }) => {
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

    // Focus the address input and press ArrowDown
    await secPage.getByPlaceholder('Search address (geocoding)...').focus();
    await secPage.keyboard.press('ArrowDown');

    const firstIsFocused = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const lb = sw?.shadowRoot?.querySelector('idispatch-lookup-bar');
        const items = lb?.shadowRoot?.querySelectorAll('.result-item');
        return items?.[0]?.classList.contains('focused') ?? false;
    });
    expect(firstIsFocused).toBe(true);

    // Press Enter to select the first item
    await secPage.keyboard.press('Enter');

    // Dropdown should close
    await secPage.waitForTimeout(300);
    const dropdownVisible = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const lb = sw?.shadowRoot?.querySelector('idispatch-lookup-bar');
        const dropdown = lb?.shadowRoot?.querySelector('.results-dropdown') as HTMLElement | null;
        return dropdown ? !dropdown.hidden : false;
    });
    expect(dropdownVisible).toBe(false);
});

test('ArrowUp from first item wraps to last; Escape closes dropdown (issue #45, item 8)', async ({ page, context }) => {
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

    await secPage.getByPlaceholder('Search address (geocoding)...').focus();
    // ArrowDown to first, then ArrowUp should wrap to last (index 1)
    await secPage.keyboard.press('ArrowDown');
    await secPage.keyboard.press('ArrowUp');

    const lastIsFocused = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const lb = sw?.shadowRoot?.querySelector('idispatch-lookup-bar');
        const items = lb?.shadowRoot?.querySelectorAll('.result-item');
        if (!items || items.length < 2) return false;
        return items[items.length - 1].classList.contains('focused');
    });
    expect(lastIsFocused).toBe(true);

    // Escape closes dropdown
    await secPage.keyboard.press('Escape');
    await secPage.waitForTimeout(200);
    const dropdownVisible = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const lb = sw?.shadowRoot?.querySelector('idispatch-lookup-bar');
        const dropdown = lb?.shadowRoot?.querySelector('.results-dropdown') as HTMLElement | null;
        return dropdown ? !dropdown.hidden : false;
    });
    expect(dropdownVisible).toBe(false);
});

// -------------------------------------------------------------------------
// Tests — issue #45, item 3: Coordinate format improvements
// -------------------------------------------------------------------------

test('DDM coordinates entered without degree symbol are accepted (issue #45, item 3)', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    await secPage.getByPlaceholder(/60/).fill('60 10.1914N 24 56.3027E');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    await secPage.waitForTimeout(500);
    const errorVisible = await secPage.locator('.inline-error:not([hidden])').isVisible().catch(() => false);
    expect(errorVisible).toBe(false);
});

test('DMS coordinates entered without degree symbol are accepted (issue #45, item 3)', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    await secPage.getByPlaceholder(/60/).fill('60 10 11.49N 24 56 18.16E');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    await secPage.waitForTimeout(500);
    const errorVisible = await secPage.locator('.inline-error:not([hidden])').isVisible().catch(() => false);
    expect(errorVisible).toBe(false);
});

test('format selector converts DDM coordinate to DD on click (issue #45, item 3)', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    // Enter a valid DDM coordinate
    await secPage.getByPlaceholder(/60/).fill("60°10.1914'N 024°56.3027'E");
    await secPage.waitForTimeout(200);

    // Click the DD format button inside the CoordInput shadow DOM
    await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const lb = sw?.shadowRoot?.querySelector('idispatch-lookup-bar');
        const ci = lb?.shadowRoot?.querySelector('idispatch-coord-input');
        const ddBtn = ci?.shadowRoot?.querySelector('.format-btn[data-format="DD"]') as HTMLElement | null;
        ddBtn?.click();
    });

    await secPage.waitForTimeout(200);

    const inputValue = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const lb = sw?.shadowRoot?.querySelector('idispatch-lookup-bar');
        const ci = lb?.shadowRoot?.querySelector('idispatch-coord-input');
        const input = ci?.shadowRoot?.querySelector('.input') as HTMLInputElement | null;
        return input?.value ?? '';
    });

    // DD format should contain decimal degrees; no minutes (') or seconds (") symbols
    expect(inputValue).toMatch(/^\d+\.\d+/);
    expect(inputValue).not.toContain("'");
    expect(inputValue).not.toContain('"');

    // Reset format for other tests
    await secPage.evaluate(() => localStorage.removeItem('idispatch:coordFormat'));
});

test('format selector DD button is keyboard-accessible (issue #45, item 3)', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    // Focus the DD button and activate it via Enter key
    await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const lb = sw?.shadowRoot?.querySelector('idispatch-lookup-bar');
        const ci = lb?.shadowRoot?.querySelector('idispatch-coord-input');
        const ddBtn = ci?.shadowRoot?.querySelector('.format-btn[data-format="DD"]') as HTMLElement | null;
        ddBtn?.focus();
    });

    await secPage.keyboard.press('Enter');
    await secPage.waitForTimeout(200);

    const ddIsActive = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const lb = sw?.shadowRoot?.querySelector('idispatch-lookup-bar');
        const ci = lb?.shadowRoot?.querySelector('idispatch-coord-input');
        const ddBtn = ci?.shadowRoot?.querySelector('.format-btn[data-format="DD"]');
        return ddBtn?.classList.contains('active') ?? false;
    });
    expect(ddIsActive).toBe(true);

    // Reset format for other tests
    await secPage.evaluate(() => localStorage.removeItem('idispatch:coordFormat'));
});

// -------------------------------------------------------------------------
// Tests — issue #45, item 5: Road name without number
// -------------------------------------------------------------------------

test('road name without number returns result and places marker (issue #45, item 5)', async ({ page, context }) => {
    await mockLayers(context);
    await mockTiles(context);
    await context.route(`${GIS_URL}/api/v1/geocode/**`, route =>
        route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
                results: [
                    {
                        type: 'address',
                        name: { fi: 'Mannerheimintie', sv: 'Mannerheimvägen' },
                        // number is absent (road name result)
                        municipality: { code: '091', name: { fi: 'Helsinki', sv: 'Helsingfors' } },
                        coordinates: { latitude: 60.169857, longitude: 24.938379 },
                    },
                    {
                        type: 'address',
                        name: { fi: 'Mannerheimintie', sv: 'Mannerheimvägen' },
                        municipality: { code: '091', name: { fi: 'Helsinki', sv: 'Helsingfors' } },
                        coordinates: { latitude: 60.180000, longitude: 24.940000 },
                    },
                ],
                query: 'Mannerheimintie',
                resultCount: 2,
            }),
        })
    );

    await loginViaKeycloak(page);
    const secPage = await openSecondaryWindow(page, context);

    await secPage.getByPlaceholder('Search address (geocoding)...').fill('Mannerheimintie');
    await secPage.getByRole('button', { name: 'Lookup' }).click();

    await expect(secPage.locator('.results-dropdown')).toBeVisible({ timeout: 5_000 });

    // First result has no number — should display just the road name
    const firstResultName = await secPage.locator('.result-name').first().textContent();
    expect(firstResultName?.trim()).toBe('Mannerheimintie');

    // Clicking it should place a marker
    await secPage.locator('.result-item').first().click();
    await secPage.waitForTimeout(500);

    const featureCount = await secPage.evaluate(() => {
        const shell = document.querySelector('idispatch-app-shell');
        const sw = shell?.shadowRoot?.querySelector('idispatch-secondary-window');
        const lb = sw?.shadowRoot?.querySelector('idispatch-lookup-bar') as any;
        return lb?.markerFeatureCount ?? -1;
    });
    expect(featureCount).toBe(1);
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

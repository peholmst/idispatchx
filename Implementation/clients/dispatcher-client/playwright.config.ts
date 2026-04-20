import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
    testDir: 'e2e',
    testMatch: '**/*.spec.ts',

    // Run tests sequentially — OIDC state is shared via sessionStorage
    workers: 1,
    fullyParallel: false,

    // Retry once on failure in CI to handle transient timing issues
    retries: process.env['CI'] ? 1 : 0,

    reporter: [['list'], ['html', { open: 'never' }]],

    use: {
        baseURL: 'http://localhost:5173',
        // Capture traces on first retry only (saves disk space)
        trace: 'on-first-retry',
        // Give ample time for Keycloak redirects
        navigationTimeout: 15_000,
        actionTimeout: 10_000,
    },

    projects: [
        {
            name: 'chromium',
            use: { ...devices['Desktop Chrome'] },
        },
    ],

    // Start the Vite dev server with the test config URL before running tests
    webServer: {
        command: 'npm run dev',
        url: 'http://localhost:5173',
        reuseExistingServer: !process.env['CI'],
        timeout: 30_000,
        env: {
            // Tells AppConfig to load /config.test.json instead of /config.json
            VITE_CONFIG_URL: '/config.test.json',
        },
    },

    globalSetup: './e2e/globalSetup.ts',
    globalTeardown: './e2e/globalTeardown.ts',
});

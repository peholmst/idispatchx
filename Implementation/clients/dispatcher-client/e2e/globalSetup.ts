// Playwright global setup: starts a Keycloak Docker container for E2E tests.
// The container ID is written to a temp file so globalTeardown can stop it.

import { execSync, spawnSync } from 'child_process';
import { writeFileSync } from 'fs';
import { join, resolve } from 'path';

const KEYCLOAK_IMAGE = 'quay.io/keycloak/keycloak:26.0.0';
const KEYCLOAK_PORT = 8180;
const CONTAINER_NAME = 'idispatchx-keycloak-test';
const CONTAINER_ID_FILE = join(process.cwd(), '.keycloak-container-id');
const STARTUP_TIMEOUT_MS = 120_000;
const POLL_INTERVAL_MS = 2_000;

export default async function globalSetup(): Promise<void> {
    console.log('\n[E2E Setup] Starting Keycloak...');

    // Stop any leftover container from a previous run
    stopExistingContainer();

    const realmJsonPath = resolve(process.cwd(), 'docker/keycloak-realm.json');

    // Start Keycloak with realm import
    const result = spawnSync('docker', [
        'run', '--detach',
        '--name', CONTAINER_NAME,
        '-p', `${KEYCLOAK_PORT}:8080`,
        '-e', 'KEYCLOAK_ADMIN=admin',
        '-e', 'KEYCLOAK_ADMIN_PASSWORD=admin',
        '-e', 'KC_HEALTH_ENABLED=true',
        '-v', `${realmJsonPath}:/opt/keycloak/data/import/realm.json`,
        KEYCLOAK_IMAGE,
        'start-dev', '--import-realm',
    ], { encoding: 'utf8' });

    if (result.status !== 0) {
        throw new Error(`Failed to start Keycloak container: ${result.stderr}`);
    }

    const containerId = result.stdout.trim();
    writeFileSync(CONTAINER_ID_FILE, containerId);

    console.log(`[E2E Setup] Keycloak container started: ${containerId.slice(0, 12)}`);
    console.log(`[E2E Setup] Waiting for Keycloak to be ready on port ${KEYCLOAK_PORT}...`);

    await waitForKeycloak();
    console.log('[E2E Setup] Keycloak is ready.\n');
}

function stopExistingContainer(): void {
    try {
        execSync(`docker stop ${CONTAINER_NAME} 2>/dev/null; docker rm ${CONTAINER_NAME} 2>/dev/null`, {
            stdio: 'ignore',
        });
    } catch {
        // Ignore errors — container may not exist
    }
}

async function waitForKeycloak(): Promise<void> {
    // Poll the OIDC discovery endpoint on the main port — this confirms both that
    // Keycloak is running AND that the test realm has been imported successfully.
    // (The management health endpoint is on port 9000 and is not exposed.)
    const healthUrl = `http://localhost:${KEYCLOAK_PORT}/realms/idispatchx/.well-known/openid-configuration`;
    const deadline = Date.now() + STARTUP_TIMEOUT_MS;

    while (Date.now() < deadline) {
        try {
            const response = await fetch(healthUrl);
            if (response.ok) return;
        } catch {
            // Not ready yet
        }
        await sleep(POLL_INTERVAL_MS);
    }

    throw new Error(
        `Keycloak did not become ready within ${STARTUP_TIMEOUT_MS / 1000}s. ` +
        `Check 'docker logs ${CONTAINER_NAME}' for details.`
    );
}

function sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
}

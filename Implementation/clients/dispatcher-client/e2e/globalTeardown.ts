// Playwright global teardown: stops and removes the Keycloak Docker container.

import { execSync } from 'child_process';
import { readFileSync, unlinkSync } from 'fs';
import { join } from 'path';

const CONTAINER_ID_FILE = join(process.cwd(), '.keycloak-container-id');

export default function globalTeardown(): void {
    console.log('\n[E2E Teardown] Stopping Keycloak...');

    let containerId: string | null = null;
    try {
        containerId = readFileSync(CONTAINER_ID_FILE, 'utf8').trim();
        unlinkSync(CONTAINER_ID_FILE);
    } catch {
        // Container ID file missing — container may have already been removed
    }

    try {
        if (containerId) {
            execSync(`docker stop ${containerId} && docker rm ${containerId}`, { stdio: 'ignore' });
        } else {
            execSync('docker stop idispatchx-keycloak-test && docker rm idispatchx-keycloak-test', {
                stdio: 'ignore',
            });
        }
        console.log('[E2E Teardown] Keycloak stopped.\n');
    } catch {
        console.warn('[E2E Teardown] Warning: could not stop Keycloak container (may already be stopped).');
    }
}

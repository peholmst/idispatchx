// @vitest-environment jsdom
//
// Regression test for issue #48: OIDC token refresh must not recreate the
// window element (which would reset the OpenLayers map view).

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// --- Mocks (must come before imports that pull in the mocked modules) ---

// SecondaryWindow imports OpenLayers — mock it out so we stay in jsdom.
vi.mock('./SecondaryWindow.ts', () => {
    class MockSecondaryWindow extends HTMLElement {
        static TAG = 'idispatch-secondary-window';
        initialize() {}
    }
    return { SecondaryWindow: MockSecondaryWindow, SECONDARY_WINDOW_OPEN_KEY: 'idispatch:window:secondary' };
});

vi.mock('./PrimaryWindow.ts', () => {
    class MockPrimaryWindow extends HTMLElement {
        static TAG = 'idispatch-primary-window';
        initialize() {}
    }
    return { PrimaryWindow: MockPrimaryWindow };
});

vi.mock('./LauncherPage.ts', () => {
    class MockLauncherPage extends HTMLElement {
        static TAG = 'idispatch-launcher-page';
        initialize() {}
    }
    return { LauncherPage: MockLauncherPage };
});

vi.mock('./LoginScreen.ts', () => {
    class MockLoginScreen extends HTMLElement {
        static TAG = 'idispatch-login-screen';
    }
    class MockLoginRequestedEvent extends Event {
        static TYPE = 'login-requested';
    }
    return { LoginScreen: MockLoginScreen, LoginRequestedEvent: MockLoginRequestedEvent };
});

vi.mock('./AppShell.css?inline', () => ({ default: '' }));

// --- End mocks ---

import { AppShell } from './AppShell.ts';
import { AuthChangedEvent } from '../auth/AuthState.ts';
import type { TokenSet } from '../auth/types.ts';

// Register custom elements once
if (!customElements.get(AppShell.TAG)) {
    customElements.define(AppShell.TAG, AppShell);
}
// Register mock secondary window
const { SecondaryWindow } = await import('./SecondaryWindow.ts');
if (!customElements.get(SecondaryWindow.TAG)) {
    customElements.define(SecondaryWindow.TAG, SecondaryWindow);
}

function makeAuthState() {
    const authState = new EventTarget() as EventTarget & {
        getStatus: () => { kind: string };
        getAccessToken: () => string | null;
        getDiscovery: () => null;
    };
    authState.getStatus = () => ({ kind: 'unauthenticated' });
    authState.getAccessToken = () => null;
    authState.getDiscovery = () => null;
    return authState;
}

function makeSessionManager() {
    return { start: vi.fn(), stop: vi.fn() };
}

function makeHttpClient() {
    return {};
}

function makeTokenSet(): TokenSet {
    return {
        accessToken: 'access',
        idToken: null,
        refreshToken: null,
        expiresAt: Date.now() + 300_000,
        parsedAccess: {
            sub: 'user1',
            exp: Math.floor((Date.now() + 300_000) / 1000),
            iat: Math.floor(Date.now() / 1000),
            iss: 'http://localhost',
            preferred_username: 'testuser',
        },
    };
}

describe('AppShell — token-refresh re-render guard', () => {
    let container: HTMLDivElement;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
    });

    afterEach(() => {
        container.remove();
    });

    it('does not recreate the secondary window on token refresh', () => {
        const authState = makeAuthState() as ReturnType<typeof makeAuthState> & EventTarget;
        const sessionManager = makeSessionManager();
        const http = makeHttpClient();

        const shell = document.createElement(AppShell.TAG) as AppShell;
        shell.initialize(authState as never, sessionManager as never, 'secondary', http as never, 'http://gis', 'http://cad');
        container.appendChild(shell);

        // First authenticated event — window is rendered for the first time
        authState.dispatchEvent(new AuthChangedEvent({ kind: 'authenticated', tokenSet: makeTokenSet() }));

        const shadow = shell.shadowRoot!;
        const firstWindowEl = shadow.querySelector(SecondaryWindow.TAG);
        expect(firstWindowEl).not.toBeNull();

        // Second authenticated event simulates a token refresh
        authState.dispatchEvent(new AuthChangedEvent({ kind: 'authenticated', tokenSet: makeTokenSet() }));

        const secondWindowEl = shadow.querySelector(SecondaryWindow.TAG);
        expect(secondWindowEl).toBe(firstWindowEl);
    });

    it('recreates the secondary window after re-authentication following expiry', () => {
        const authState = makeAuthState() as ReturnType<typeof makeAuthState> & EventTarget;
        const sessionManager = makeSessionManager();
        const http = makeHttpClient();

        const shell = document.createElement(AppShell.TAG) as AppShell;
        shell.initialize(authState as never, sessionManager as never, 'secondary', http as never, 'http://gis', 'http://cad');
        container.appendChild(shell);

        authState.dispatchEvent(new AuthChangedEvent({ kind: 'authenticated', tokenSet: makeTokenSet() }));
        const shadow = shell.shadowRoot!;
        const firstWindowEl = shadow.querySelector(SecondaryWindow.TAG);
        expect(firstWindowEl).not.toBeNull();

        // Session expires
        authState.dispatchEvent(new AuthChangedEvent({ kind: 'expired', reason: 'idle-timeout' }));
        expect(shadow.querySelector(SecondaryWindow.TAG)).toBeNull();

        // User re-authenticates
        authState.dispatchEvent(new AuthChangedEvent({ kind: 'authenticated', tokenSet: makeTokenSet() }));
        const reAuthWindowEl = shadow.querySelector(SecondaryWindow.TAG);
        expect(reAuthWindowEl).not.toBeNull();
        expect(reAuthWindowEl).not.toBe(firstWindowEl);
    });
});

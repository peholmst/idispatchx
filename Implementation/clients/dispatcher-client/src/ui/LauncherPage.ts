// Launcher page Web Component — shown after login.
// Provides actions to open the primary and secondary dispatcher windows,
// sign out, and navigate to OIDC account management.

import STYLES from './LauncherPage.css?inline';
import type { AuthState } from '../auth/AuthState.ts';
import { SESSION_CHANNEL_NAME } from './windowType.ts';
import { PRIMARY_WINDOW_OPEN_KEY } from './PrimaryWindow.ts';
import { SECONDARY_WINDOW_OPEN_KEY } from './SecondaryWindow.ts';

/** Feature string for window.open — no browser chrome, sized for Full HD monitors. */
const WINDOW_FEATURES = 'width=1920,height=1080,menubar=no,toolbar=no,location=no,status=no';

/** Polling interval (ms) to detect when a dispatcher window has been closed. */
const CLOSED_POLL_INTERVAL_MS = 1000;

/**
 * `<idispatch-launcher-page>` Web Component.
 *
 * Single-instance enforcement uses three complementary layers:
 *   1. Named window target in window.open() — browser reuses the same window.
 *   2. In-memory Window ref + .closed check — detects closure within the session.
 *   3. sessionStorage flags written by the child windows via window.opener — survives
 *      a launcher page refresh that would otherwise clear the in-memory refs.
 *
 * Accidental-close protection: a beforeunload handler is registered on the launcher
 * window whenever a dispatcher window is open. It triggers the browser's built-in
 * "Leave site?" confirmation dialog to prevent inadvertent tab closure.
 */
export class LauncherPage extends HTMLElement {
    static readonly TAG = 'idispatch-launcher-page' as const;

    #shadow: ShadowRoot;
    #authState: AuthState | null = null;
    #username = '';

    // Retained handles to opened windows; checked via .closed
    #primaryWindowRef: Window | null = null;
    #secondaryWindowRef: Window | null = null;

    // Poll timer that nulls refs when windows are detected closed
    #pollId: ReturnType<typeof setInterval> | null = null;

    // beforeunload guard — registered when any dispatcher window is open
    #beforeUnloadHandler: ((e: BeforeUnloadEvent) => void) | null = null;

    // BroadcastChannel used to propagate session events (e.g. sign-out) to all
    // open dispatcher windows so they respond immediately.
    #sessionChannel: BroadcastChannel | null = null;

    constructor() {
        super();
        this.#shadow = this.attachShadow({ mode: 'open' });
    }

    initialize(authState: AuthState, username: string): void {
        this.#authState = authState;
        this.#username = username;
    }

    connectedCallback(): void {
        // Open the broadcast channel so sign-out can be propagated to all open
        // dispatcher windows the moment the user clicks Sign Out.
        this.#sessionChannel = new BroadcastChannel(SESSION_CHANNEL_NAME);

        // If this launcher was refreshed while dispatcher windows were open, the
        // in-memory refs are gone but the sessionStorage flags remain (written by
        // the child windows via window.opener). Re-arm the beforeunload guard so
        // the dispatcher is still warned before accidentally closing the launcher.
        if (this.#anyWindowFlagSet()) {
            this.#registerBeforeUnload();
        }

        const style = document.createElement('style');
        style.textContent = STYLES;

        const card = document.createElement('div');
        card.className = 'launcher-card';

        const logo = document.createElement('div');
        logo.className = 'launcher-logo';
        logo.textContent = 'iD';
        logo.setAttribute('aria-hidden', 'true');

        const title = document.createElement('h1');
        title.className = 'launcher-title';
        title.textContent = 'iDispatchX';

        const usernameEl = document.createElement('div');
        usernameEl.className = 'launcher-username';
        usernameEl.textContent = this.#username;

        const actions = document.createElement('div');
        actions.className = 'launcher-actions';

        const openPrimaryBtn = this.#makeButton(
            'Open Primary Window',
            'launcher-btn primary-action open-primary-btn',
            () => { this.#openWindow('primary'); },
        );

        const openSecondaryBtn = this.#makeButton(
            'Open Secondary Window',
            'launcher-btn primary-action open-secondary-btn',
            () => { this.#openWindow('secondary'); },
        );

        const divider = document.createElement('hr');
        divider.className = 'launcher-divider';

        const accountBtn = this.#makeButton(
            'Account Management',
            'launcher-btn',
            () => { this.#openAccountManagement(); },
        );

        const signOutBtn = this.#makeButton(
            'Sign Out',
            'launcher-btn launcher-btn-signout',
            () => {
                // Notify all open dispatcher windows before redirecting so they
                // can show the signed-out overlay immediately rather than waiting
                // for a token expiry or a 401.
                this.#sessionChannel?.postMessage({ type: 'sign-out' });
                void this.#authState?.logout();
            },
        );

        actions.append(openPrimaryBtn, openSecondaryBtn, divider, accountBtn, signOutBtn);
        card.append(logo, title, usernameEl, actions);
        this.#shadow.append(style, card);
    }

    disconnectedCallback(): void {
        this.#sessionChannel?.close();
        this.#sessionChannel = null;
        this.#stopPolling();
        this.#unregisterBeforeUnload();
    }

    // -------------------------------------------------------------------------
    // Window management
    // -------------------------------------------------------------------------

    #openWindow(type: 'primary' | 'secondary'): void {
        const existingRef = type === 'primary' ? this.#primaryWindowRef : this.#secondaryWindowRef;
        const windowName = `idispatch-${type}` as const;
        const flagKey = type === 'primary' ? PRIMARY_WINDOW_OPEN_KEY : SECONDARY_WINDOW_OPEN_KEY;

        // Layer 2: in-memory ref check — focus if still open
        if (existingRef !== null && !existingRef.closed) {
            existingRef.focus();
            return;
        }

        // Layer 1: if the sessionStorage flag is set the window announced itself
        // as open during this launcher session. The launcher may have been
        // refreshed since, losing the in-memory ref. Probe with an empty URL to
        // focus the window without navigating (reloading) it.
        //
        // The probe is guarded behind the flag so that the normal first-open path
        // goes straight to window.open(url), avoiding a spurious about:blank popup
        // that would otherwise appear and immediately be closed.
        if (sessionStorage.getItem(flagKey) === '1') {
            const probed = window.open('', windowName);
            if (probed !== null && probed.location.href !== 'about:blank') {
                // Existing window found — recover the ref and focus without reload
                if (type === 'primary') this.#primaryWindowRef = probed;
                else this.#secondaryWindowRef = probed;
                probed.focus();
                this.#registerBeforeUnload();
                this.#startPolling();
                return;
            }
            // Flag was stale (window crashed or closed without clearing it).
            // Clean up and fall through to open a fresh window.
            sessionStorage.removeItem(flagKey);
            probed?.close();
        }

        // Open the real URL with the correct window features
        const newWin = window.open(`?window=${type}`, windowName, WINDOW_FEATURES);
        if (newWin === null) {
            // Popup was blocked — the browser shows its own indicator
            console.warn(`[LauncherPage] Popup blocked for ${type} window`);
            return;
        }

        if (type === 'primary') this.#primaryWindowRef = newWin;
        else this.#secondaryWindowRef = newWin;

        this.#registerBeforeUnload();
        this.#startPolling();
    }

    #openAccountManagement(): void {
        const issuer = this.#authState?.getDiscovery()?.issuer;
        if (issuer) {
            window.open(`${issuer}/account`, '_blank', 'noopener,noreferrer');
        }
    }

    // -------------------------------------------------------------------------
    // Polling — detects when child windows are closed
    // -------------------------------------------------------------------------

    #startPolling(): void {
        if (this.#pollId !== null) return; // already running

        this.#pollId = setInterval(() => {
            if (this.#primaryWindowRef?.closed) {
                this.#primaryWindowRef = null;
            }
            if (this.#secondaryWindowRef?.closed) {
                this.#secondaryWindowRef = null;
            }

            // When all windows are confirmed closed, tear down guards
            if (!this.#anyWindowOpen()) {
                this.#stopPolling();
                this.#unregisterBeforeUnload();
            }
        }, CLOSED_POLL_INTERVAL_MS);
    }

    #stopPolling(): void {
        if (this.#pollId !== null) {
            clearInterval(this.#pollId);
            this.#pollId = null;
        }
    }

    // -------------------------------------------------------------------------
    // beforeunload guard — warns before accidental launcher closure
    // -------------------------------------------------------------------------

    #registerBeforeUnload(): void {
        if (this.#beforeUnloadHandler) return; // already registered

        this.#beforeUnloadHandler = (e: BeforeUnloadEvent) => {
            if (this.#anyWindowOpen()) {
                e.preventDefault();
                // Assigning returnValue is required for compatibility with older browsers
                // that do not respect preventDefault() alone.
                e.returnValue = '';
            }
        };
        window.addEventListener('beforeunload', this.#beforeUnloadHandler);
    }

    #unregisterBeforeUnload(): void {
        if (this.#beforeUnloadHandler) {
            window.removeEventListener('beforeunload', this.#beforeUnloadHandler);
            this.#beforeUnloadHandler = null;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns true if any dispatcher window is believed to be open. */
    #anyWindowOpen(): boolean {
        return (
            (this.#primaryWindowRef !== null && !this.#primaryWindowRef.closed) ||
            (this.#secondaryWindowRef !== null && !this.#secondaryWindowRef.closed) ||
            this.#anyWindowFlagSet()
        );
    }

    /**
     * Checks the sessionStorage flags written by child windows via window.opener.
     * Returns true if at least one flag is present, indicating a dispatcher window
     * has announced itself in the current launcher session.
     */
    #anyWindowFlagSet(): boolean {
        return (
            sessionStorage.getItem(PRIMARY_WINDOW_OPEN_KEY) === '1' ||
            sessionStorage.getItem(SECONDARY_WINDOW_OPEN_KEY) === '1'
        );
    }

    #makeButton(text: string, className: string, onClick: () => void): HTMLButtonElement {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = className;
        btn.textContent = text;
        btn.addEventListener('click', onClick);
        return btn;
    }
}

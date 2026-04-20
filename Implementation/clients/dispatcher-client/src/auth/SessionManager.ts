// Enforces idle session timeout and maximum session lifetime per NFR Security:
// "Dispatcher Client and Admin Client must have idle session timeouts."
// "All clients must have a maximum session lifetime, after which re-authentication is required."

import type { SessionConfig } from '../config/AppConfig.ts';
import { AuthState, SessionWarningEvent } from './AuthState.ts';

/** How often to check session expiry (every 30 seconds). */
const CHECK_INTERVAL_MS = 30_000;

/** Warn the user this many seconds before a session limit is reached. */
const WARNING_THRESHOLD_S = 300;

export class SessionManager {
    #lastActivity: number = Date.now();
    #intervalId: ReturnType<typeof setInterval> | null = null;
    #activityHandler: () => void;

    constructor(
        private readonly authState: AuthState,
        private readonly config: SessionConfig,
    ) {
        this.#activityHandler = () => {
            this.#lastActivity = Date.now();
        };
    }

    /** Start activity tracking and session expiry checks. */
    start(): void {
        if (this.#intervalId !== null) return; // already running

        this.#lastActivity = Date.now();

        // Listen for any user activity to reset the idle clock
        document.addEventListener('mousemove', this.#activityHandler, { passive: true });
        document.addEventListener('keydown', this.#activityHandler, { passive: true });
        document.addEventListener('click', this.#activityHandler, { passive: true });
        document.addEventListener('touchstart', this.#activityHandler, { passive: true });

        this.#intervalId = setInterval(() => this.#check(), CHECK_INTERVAL_MS);
    }

    /** Stop activity tracking and session expiry checks (called on logout or expiry). */
    stop(): void {
        if (this.#intervalId !== null) {
            clearInterval(this.#intervalId);
            this.#intervalId = null;
        }
        document.removeEventListener('mousemove', this.#activityHandler);
        document.removeEventListener('keydown', this.#activityHandler);
        document.removeEventListener('click', this.#activityHandler);
        document.removeEventListener('touchstart', this.#activityHandler);
    }

    #check(): void {
        const now = Date.now();

        // --- Idle timeout check ---
        const idleSecs = (now - this.#lastActivity) / 1000;
        const idleRemaining = this.config.idleTimeoutSeconds - idleSecs;

        if (idleRemaining <= 0) {
            this.stop();
            this.authState.forceLogout('idle-timeout');
            return;
        }

        if (idleRemaining <= WARNING_THRESHOLD_S) {
            this.authState.dispatchEvent(
                new SessionWarningEvent('idle-timeout', Math.ceil(idleRemaining)),
            );
        }

        // --- Max session lifetime check ---
        const loginAtRaw = sessionStorage.getItem('idispatchx.login_at');
        if (loginAtRaw !== null) {
            const loginAt = parseInt(loginAtRaw, 10);
            if (!isNaN(loginAt)) {
                const sessionAgeSecs = (now - loginAt) / 1000;
                const lifetimeRemaining = this.config.maxLifetimeSeconds - sessionAgeSecs;

                if (lifetimeRemaining <= 0) {
                    this.stop();
                    this.authState.forceLogout('max-lifetime');
                    return;
                }

                if (lifetimeRemaining <= WARNING_THRESHOLD_S) {
                    this.authState.dispatchEvent(
                        new SessionWarningEvent('max-lifetime', Math.ceil(lifetimeRemaining)),
                    );
                }
            }
        }
    }
}

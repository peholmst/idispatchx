// Shared window type constants — kept in a separate file to avoid circular
// imports between main.ts (which registers components) and AppShell.ts
// (which imports components and main.ts).

export type WindowType = 'launcher' | 'primary' | 'secondary';

/** sessionStorage key used to persist the window type across OIDC redirects. */
export const WINDOW_TYPE_KEY = 'idispatch:windowType';

/**
 * BroadcastChannel name for session-level coordination between the launcher
 * and open dispatcher windows (e.g. sign-out propagation).
 */
export const SESSION_CHANNEL_NAME = 'idispatch-session';

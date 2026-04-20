// Shared window type constants — kept in a separate file to avoid circular
// imports between main.ts (which registers components) and AppShell.ts
// (which imports components and main.ts).

export type WindowType = 'launcher' | 'primary' | 'secondary';

/** sessionStorage key used to persist the window type across OIDC redirects. */
export const WINDOW_TYPE_KEY = 'idispatch:windowType';

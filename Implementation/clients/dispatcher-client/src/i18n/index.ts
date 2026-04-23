// Internationalisation module for Dispatcher Client.
//
// Supported locales: English (en), Finnish (fi), Swedish (sv).
// English is the fallback when a translation key is absent in the active locale.
//
// Locale is persisted in localStorage so it survives logout and page reload.
// Changing the locale triggers a full page reload (acceptable per NFR and UX guidelines).

export type Locale = 'en' | 'fi' | 'sv';

const LOCALE_STORAGE_KEY = 'idispatch:locale';

const SUPPORTED_LOCALES: ReadonlySet<string> = new Set<Locale>(['en', 'fi', 'sv']);

// ---------------------------------------------------------------------------
// Translation key catalogue
// ---------------------------------------------------------------------------

export type TranslationKey =
    | 'launcher.openPrimaryWindow'
    | 'launcher.openSecondaryWindow'
    | 'launcher.accountManagement'
    | 'launcher.signOut'
    | 'launcher.language'
    | 'primaryWindow.newCall'
    | 'primaryWindow.newIncident'
    | 'footer.normalMode'
    | 'login.signingIn'
    | 'login.sessionExpired'
    | 'login.idleTimeout'
    | 'login.maxLifetime'
    | 'login.forcedLogout'
    | 'login.signInAgain'
    | 'lookup.addressLabel'
    | 'lookup.addressPlaceholder'
    | 'lookup.coordsLabel'
    | 'lookup.coordsPlaceholder'
    | 'lookup.button'
    | 'lookup.clear'
    | 'lookup.noResults'
    | 'lookup.unavailable'
    | 'lookup.timeout'
    | 'lookup.tooShort'
    | 'lookup.coordsOutOfBounds'
    | 'lookup.coordsInvalid'
    | 'lookup.typeAddress'
    | 'lookup.typeIntersection'
    | 'lookup.typePlace'
    | 'lookup.layers.loading'
    | 'lookup.layers.unavailable';

type Translations = Record<TranslationKey, string>;

// ---------------------------------------------------------------------------
// Translation tables
// ---------------------------------------------------------------------------

const EN: Translations = {
    'launcher.openPrimaryWindow':   'Open Primary Window',
    'launcher.openSecondaryWindow': 'Open Secondary Window',
    'launcher.accountManagement':   'Account Management',
    'launcher.signOut':             'Sign Out',
    'launcher.language':            'Language',
    'primaryWindow.newCall':        'New Call',
    'primaryWindow.newIncident':    'New Incident',
    'footer.normalMode':            'Normal',
    // NOTE: the word "inactivity" in the idle-timeout string is load-bearing —
    // auth.spec.ts asserts that the message contains this word.
    'login.signingIn':              'Signing in\u2026',
    'login.sessionExpired':         'You have been signed out.',
    'login.idleTimeout':            'You were signed out due to inactivity.',
    'login.maxLifetime':            'Your session has reached its maximum duration.',
    'login.forcedLogout':           'You have been signed out.',
    'login.signInAgain':            'Sign in again',
    'lookup.addressLabel':          'Address',
    'lookup.addressPlaceholder':    'Search address (geocoding)...',
    'lookup.coordsLabel':           'Coordinates',
    'lookup.coordsPlaceholder':     'e.g. 60°10.220′N 024°56.380′E',
    'lookup.button':                'Lookup',
    'lookup.clear':                 'Clear',
    'lookup.noResults':             'No locations found',
    'lookup.unavailable':           'Address lookup is temporarily unavailable',
    'lookup.timeout':               'The request took too long. Please retry.',
    'lookup.tooShort':              'Enter at least 3 characters',
    'lookup.coordsOutOfBounds':     'Coordinates are outside the supported area',
    'lookup.coordsInvalid':         'Invalid coordinate format',
    'lookup.typeAddress':           'Address',
    'lookup.typeIntersection':      'Intersection',
    'lookup.typePlace':             'Place',
    'lookup.layers.loading':        'Loading...',
    'lookup.layers.unavailable':    '—',
};

const FI: Translations = {
    'launcher.openPrimaryWindow':   'Avaa ensisijainen ikkuna',
    'launcher.openSecondaryWindow': 'Avaa toissijainen ikkuna',
    'launcher.accountManagement':   'Tilin hallinta',
    'launcher.signOut':             'Kirjaudu ulos',
    'launcher.language':            'Kieli',
    'primaryWindow.newCall':        'Uusi puhelu',
    'primaryWindow.newIncident':    'Uusi tehtävä',
    'footer.normalMode':            'Normaali',
    'login.signingIn':              'Kirjaudutaan sisään\u2026',
    'login.sessionExpired':         'Sinut on kirjattu ulos.',
    'login.idleTimeout':            'Sinut kirjattiin ulos passiivisuuden vuoksi.',
    'login.maxLifetime':            'Istuntosi on saavuttanut enimmäiskestonsa.',
    'login.forcedLogout':           'Sinut on kirjattu ulos.',
    'login.signInAgain':            'Kirjaudu uudelleen',
    'lookup.addressLabel':          'Osoite',
    'lookup.addressPlaceholder':    'Hae osoitetta...',
    'lookup.coordsLabel':           'Koordinaatit',
    'lookup.coordsPlaceholder':     'esim. 60°10.220′P 024°56.380′I',
    'lookup.button':                'Hae',
    'lookup.clear':                 'Tyhjennä',
    'lookup.noResults':             'Sijainteja ei löydy',
    'lookup.unavailable':           'Osoitehaku ei ole tilapäisesti käytettävissä',
    'lookup.timeout':               'Pyyntö kesti liian kauan. Yritä uudelleen.',
    'lookup.tooShort':              'Kirjoita vähintään 3 merkkiä',
    'lookup.coordsOutOfBounds':     'Koordinaatit ovat tuetun alueen ulkopuolella',
    'lookup.coordsInvalid':         'Virheellinen koordinaattimuoto',
    'lookup.typeAddress':           'Osoite',
    'lookup.typeIntersection':      'Risteys',
    'lookup.typePlace':             'Paikka',
    'lookup.layers.loading':        'Ladataan...',
    'lookup.layers.unavailable':    '—',
};

const SV: Translations = {
    'launcher.openPrimaryWindow':   'Öppna primärt fönster',
    'launcher.openSecondaryWindow': 'Öppna sekundärt fönster',
    'launcher.accountManagement':   'Kontohantering',
    'launcher.signOut':             'Logga ut',
    'launcher.language':            'Språk',
    'primaryWindow.newCall':        'Nytt samtal',
    'primaryWindow.newIncident':    'Nytt uppdrag',
    'footer.normalMode':            'Normal',
    'login.signingIn':              'Loggar in\u2026',
    'login.sessionExpired':         'Du har loggats ut.',
    'login.idleTimeout':            'Du loggades ut på grund av inaktivitet.',
    'login.maxLifetime':            'Din session har nått sin maximala varaktighet.',
    'login.forcedLogout':           'Du har loggats ut.',
    'login.signInAgain':            'Logga in igen',
    'lookup.addressLabel':          'Adress',
    'lookup.addressPlaceholder':    'Sök adress...',
    'lookup.coordsLabel':           'Koordinater',
    'lookup.coordsPlaceholder':     't.ex. 60°10.220′N 024°56.380′E',
    'lookup.button':                'Sök',
    'lookup.clear':                 'Rensa',
    'lookup.noResults':             'Inga platser hittades',
    'lookup.unavailable':           'Adresssökning är tillfälligt otillgänglig',
    'lookup.timeout':               'Förfrågan tog för lång tid. Försök igen.',
    'lookup.tooShort':              'Ange minst 3 tecken',
    'lookup.coordsOutOfBounds':     'Koordinaterna är utanför det stödda området',
    'lookup.coordsInvalid':         'Ogiltigt koordinatformat',
    'lookup.typeAddress':           'Adress',
    'lookup.typeIntersection':      'Korsning',
    'lookup.typePlace':             'Plats',
    'lookup.layers.loading':        'Laddar...',
    'lookup.layers.unavailable':    '—',
};

const LOCALE_MAP: Record<Locale, Translations> = { en: EN, fi: FI, sv: SV };

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Returns the active locale.
 * Reads from localStorage; defaults to 'en' if absent or unrecognised.
 */
export function getLocale(): Locale {
    const stored = localStorage.getItem(LOCALE_STORAGE_KEY);
    return (stored !== null && SUPPORTED_LOCALES.has(stored)) ? (stored as Locale) : 'en';
}

/**
 * Persists the given locale to localStorage and reloads the page so the
 * new locale is applied everywhere (acceptable per NFR and UX guidelines).
 */
export function setLocale(locale: Locale): void {
    localStorage.setItem(LOCALE_STORAGE_KEY, locale);
    location.reload();
}

/**
 * Returns the translation for the given key in the active locale.
 * Falls back to the English string if the key is absent in the active locale.
 */
export function t(key: TranslationKey): string {
    const locale = getLocale();
    return LOCALE_MAP[locale][key] ?? EN[key];
}

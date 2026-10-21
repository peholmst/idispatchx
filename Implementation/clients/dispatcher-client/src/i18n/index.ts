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
    | 'primaryWindow.empty'
    | 'footer.normalMode'
    | 'footer.degradedMode'
    | 'footer.degradedMode.noServer'
    | 'footer.degradedMode.noArchive'
    | 'footer.degradedMode.noGis'
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
    | 'lookup.coordsPlaceholder.DD'
    | 'lookup.coordsPlaceholder.DDM'
    | 'lookup.coordsPlaceholder.DMS'
    | 'lookup.toolbar.base'
    | 'lookup.toolbar.layers'
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
    | 'lookup.layers.unavailable'
    | 'location.title'
    | 'location.municipality'
    | 'location.locationName'
    | 'location.referencePlace'
    | 'location.number'
    | 'location.crossStreet'
    | 'location.or'
    | 'location.details'
    | 'location.approximate'
    | 'location.variant.exactAddress'
    | 'location.variant.roadIntersection'
    | 'location.variant.namedPlace'
    | 'location.variant.relativeLocation'
    | 'location.error.bothNumberAndCrossStreet'
    | 'location.error.detailsRequired'
    | 'call.callerName'
    | 'call.callerPhone'
    | 'call.description'
    | 'call.outcome'
    | 'call.outcome.none'
    | 'call.outcome.callerAdvised'
    | 'call.outcome.hoax'
    | 'call.outcome.accidental'
    | 'call.outcome.otherNoActionsTaken'
    | 'call.outcome.incident_created'
    | 'call.outcome.attached_to_incident'
    | 'call.outcomeRationale'
    | 'call.empty'
    | 'call.action.endCall'
    | 'call.action.createIncident'
    | 'call.action.attachToIncident'
    | 'call.action.detachFromIncident'
    | 'call.action.copyLocation'
    | 'call.action.copyLocationDisabled'
    | 'call.action.selectIncident'
    | 'call.action.cancel'
    | 'call.error.outcomeRequired'
    | 'call.error.noIncidents'
    | 'callList.header.started'
    | 'callList.header.callerName'
    | 'callList.header.callerPhone'
    | 'callList.header.location'
    | 'callList.header.state'
    | 'callList.header.outcome'
    | 'callList.header.dispatcher'
    | 'callList.filter.placeholder'
    | 'callList.empty'
    | 'incidentList.header.created'
    | 'incidentList.header.type'
    | 'incidentList.header.priority'
    | 'incidentList.header.location'
    | 'incidentList.header.state'
    | 'incidentList.header.linkedCalls'
    | 'incidentList.filter.placeholder'
    | 'incidentList.showEnded'
    | 'incidentList.empty'
    | 'incident.state.new'
    | 'incident.state.queued'
    | 'incident.state.active'
    | 'incident.state.monitored'
    | 'incident.state.ended'
    | 'vicinity.banner'
    | 'vicinity.clear'
    | 'ws.disconnected'
    | 'ws.reconnecting'
    | 'gis.unavailable.unauthorized';

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
    'footer.degradedMode':          'Degraded',
    'footer.degradedMode.noServer': 'Degraded — CAD server unreachable',
    'footer.degradedMode.noArchive':'Degraded — archive database unavailable',
    'footer.degradedMode.noGis':    'Degraded — GIS server unavailable',
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
    'lookup.coordsPlaceholder.DD':  'e.g. 60.170278N 024.939722E',
    'lookup.coordsPlaceholder.DDM': 'e.g. 60°10.220′N 024°56.380′E',
    'lookup.coordsPlaceholder.DMS': 'e.g. 60°10′13.2″N 024°56′22.8″E',
    'lookup.toolbar.base':          'Base:',
    'lookup.toolbar.layers':        'Layers:',
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
    'location.title':               'Location',
    'location.municipality':        'Municipality',
    'location.locationName':        'Location name',
    'location.referencePlace':      'Reference place',
    'location.number':              'Number',
    'location.crossStreet':         'Cross street',
    'location.or':                  'or',
    'location.details':             'Details',
    'location.approximate':         'Approximate location',
    'location.variant.exactAddress':     'Exact Address',
    'location.variant.roadIntersection': 'Intersection',
    'location.variant.namedPlace':       'Named Place',
    'location.variant.relativeLocation': 'Relative Location',
    'location.error.bothNumberAndCrossStreet': 'Enter either a number or a cross street, not both.',
    'location.error.detailsRequired':    'Details are required for relative location.',
    'call.callerName':              'Caller name',
    'call.callerPhone':             'Caller phone',
    'call.description':             'Description',
    'call.outcome':                 'Outcome',
    'call.outcome.none':            'No outcome',
    'call.outcome.callerAdvised':   'Caller advised',
    'call.outcome.hoax':            'Hoax',
    'call.outcome.accidental':      'Accidental',
    'call.outcome.otherNoActionsTaken':  'Other — no actions taken',
    'call.outcome.incident_created':     'Incident created',
    'call.outcome.attached_to_incident': 'Attached to incident',
    'call.outcomeRationale':        'Outcome rationale',
    'call.empty':                   'No call selected. Click New Call or select a call from the list.',
    'call.action.endCall':          'End Call',
    'call.action.createIncident':   'Create Incident',
    'call.action.attachToIncident': 'Attach to Incident',
    'call.action.detachFromIncident': 'Detach from Incident',
    'call.action.copyLocation':     'Copy Location to Incident',
    'call.action.copyLocationDisabled': 'Full incident editing is not yet available.',
    'call.action.selectIncident':   'Select incident to attach to',
    'call.action.cancel':           'Cancel',
    'call.error.outcomeRequired':   'Please set an outcome before ending the call.',
    'call.error.noIncidents':       'No active incidents available to attach to.',
    'callList.header.started':      'Started',
    'callList.header.callerName':   'Caller',
    'callList.header.callerPhone':  'Phone',
    'callList.header.location':     'Location',
    'callList.header.state':        'State',
    'callList.header.outcome':      'Outcome',
    'callList.header.dispatcher':   'Dispatcher',
    'callList.filter.placeholder':  'Filter calls...',
    'callList.empty':               'No active calls.',
    'incidentList.header.created':  'Created',
    'incidentList.header.type':     'Type',
    'incidentList.header.priority': 'Priority',
    'incidentList.header.location': 'Location',
    'incidentList.header.state':    'State',
    'incidentList.header.linkedCalls': 'Calls',
    'incidentList.filter.placeholder': 'Filter incidents...',
    'incidentList.showEnded':       'Show ended',
    'incidentList.empty':           'No incidents.',
    'incident.state.new':           'New',
    'incident.state.queued':        'Queued',
    'incident.state.active':        'Active',
    'incident.state.monitored':     'Monitored',
    'incident.state.ended':         'Ended',
    'vicinity.banner':              'Showing nearby calls and incidents',
    'vicinity.clear':               'Clear filter',
    'ws.disconnected':              'Disconnected',
    'ws.reconnecting':              'Reconnecting…',
    'primaryWindow.empty':          'Select a call or create a new one.',
    'gis.unavailable.unauthorized': 'Map unavailable: the GIS server rejected the access token. Contact your administrator.',
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
    'footer.degradedMode':          'Alennettu',
    'footer.degradedMode.noServer': 'Alennettu — CAD-palvelin ei tavoitettavissa',
    'footer.degradedMode.noArchive':'Alennettu — arkistotietokanta ei käytettävissä',
    'footer.degradedMode.noGis':    'Alennettu — GIS-palvelin ei käytettävissä',
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
    'lookup.coordsPlaceholder.DD':  'esim. 60.170278P 024.939722I',
    'lookup.coordsPlaceholder.DDM': 'esim. 60°10.220′P 024°56.380′I',
    'lookup.coordsPlaceholder.DMS': 'esim. 60°10′13.2″P 024°56′22.8″I',
    'lookup.toolbar.base':          'Pohjakartta:',
    'lookup.toolbar.layers':        'Tasot:',
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
    'location.title':               'Sijainti',
    'location.municipality':        'Kunta',
    'location.locationName':        'Paikannimi',
    'location.referencePlace':      'Referenssipaikka',
    'location.number':              'Numero',
    'location.crossStreet':         'Ristikatu',
    'location.or':                  'tai',
    'location.details':             'Lisätiedot',
    'location.approximate':         'Likimääräinen sijainti',
    'location.variant.exactAddress':     'Tarkka osoite',
    'location.variant.roadIntersection': 'Risteys',
    'location.variant.namedPlace':       'Nimetty paikka',
    'location.variant.relativeLocation': 'Suhteellinen sijainti',
    'location.error.bothNumberAndCrossStreet': 'Anna joko numero tai ristikatu, ei molempia.',
    'location.error.detailsRequired':    'Lisätiedot ovat pakollisia suhteelliselle sijainnille.',
    'call.callerName':              'Soittajan nimi',
    'call.callerPhone':             'Soittajan puhelin',
    'call.description':             'Kuvaus',
    'call.outcome':                 'Tulos',
    'call.outcome.none':            'Ei tulosta',
    'call.outcome.callerAdvised':   'Soittajaa neuvottu',
    'call.outcome.hoax':            'Väärä hälytys',
    'call.outcome.accidental':      'Vahingossa',
    'call.outcome.otherNoActionsTaken':  'Muu — ei toimenpiteitä',
    'call.outcome.incident_created':     'Tehtävä luotu',
    'call.outcome.attached_to_incident': 'Liitetty tehtävään',
    'call.outcomeRationale':        'Tuloksen perustelu',
    'call.empty':                   'Puhelua ei valittu. Luo uusi puhelu tai valitse lista.',
    'call.action.endCall':          'Lopeta puhelu',
    'call.action.createIncident':   'Luo tehtävä',
    'call.action.attachToIncident': 'Liitä tehtävään',
    'call.action.detachFromIncident': 'Irrota tehtävästä',
    'call.action.copyLocation':     'Kopioi sijainti tehtävään',
    'call.action.copyLocationDisabled': 'Tehtävän muokkaus ei ole vielä käytettävissä.',
    'call.action.selectIncident':   'Valitse tehtävä',
    'call.action.cancel':           'Peruuta',
    'call.error.outcomeRequired':   'Aseta tulos ennen puhelun päättämistä.',
    'call.error.noIncidents':       'Ei aktiivisia tehtäviä.',
    'callList.header.started':      'Alkanut',
    'callList.header.callerName':   'Soittaja',
    'callList.header.callerPhone':  'Puhelin',
    'callList.header.location':     'Sijainti',
    'callList.header.state':        'Tila',
    'callList.header.outcome':      'Tulos',
    'callList.header.dispatcher':   'Päivystäjä',
    'callList.filter.placeholder':  'Suodata puheluita...',
    'callList.empty':               'Ei aktiivisia puheluita.',
    'incidentList.header.created':  'Luotu',
    'incidentList.header.type':     'Tyyppi',
    'incidentList.header.priority': 'Prioriteetti',
    'incidentList.header.location': 'Sijainti',
    'incidentList.header.state':    'Tila',
    'incidentList.header.linkedCalls': 'Puhelut',
    'incidentList.filter.placeholder': 'Suodata tehtäviä...',
    'incidentList.showEnded':       'Näytä päättyneet',
    'incidentList.empty':           'Ei tehtäviä.',
    'incident.state.new':           'Uusi',
    'incident.state.queued':        'Jonossa',
    'incident.state.active':        'Aktiivinen',
    'incident.state.monitored':     'Seurannassa',
    'incident.state.ended':         'Päättynyt',
    'vicinity.banner':              'Näytetään lähellä olevat puhelut ja tehtävät',
    'vicinity.clear':               'Poista suodatin',
    'ws.disconnected':              'Yhteys katkaistu',
    'ws.reconnecting':              'Yhdistetään uudelleen…',
    'primaryWindow.empty':          'Valitse puhelu tai luo uusi.',
    'gis.unavailable.unauthorized': 'Kartta ei ole käytettävissä: GIS-palvelin hylkäsi käyttöoikeustunnuksen. Ota yhteys järjestelmänvalvojaan.',
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
    'footer.degradedMode':          'Degraderat',
    'footer.degradedMode.noServer': 'Degraderat — CAD-servern är inte nåbar',
    'footer.degradedMode.noArchive':'Degraderat — arkivdatabasen är inte tillgänglig',
    'footer.degradedMode.noGis':    'Degraderat — GIS-servern är inte tillgänglig',
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
    'lookup.coordsPlaceholder.DD':  't.ex. 60.170278N 024.939722E',
    'lookup.coordsPlaceholder.DDM': 't.ex. 60°10.220′N 024°56.380′E',
    'lookup.coordsPlaceholder.DMS': 't.ex. 60°10′13.2″N 024°56′22.8″E',
    'lookup.toolbar.base':          'Bakgrundskarta:',
    'lookup.toolbar.layers':        'Lager:',
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
    'location.title':               'Plats',
    'location.municipality':        'Kommun',
    'location.locationName':        'Platsnamn',
    'location.referencePlace':      'Referensplats',
    'location.number':              'Nummer',
    'location.crossStreet':         'Korsningsgata',
    'location.or':                  'eller',
    'location.details':             'Detaljer',
    'location.approximate':         'Ungefärlig plats',
    'location.variant.exactAddress':     'Exakt adress',
    'location.variant.roadIntersection': 'Korsning',
    'location.variant.namedPlace':       'Namngiven plats',
    'location.variant.relativeLocation': 'Relativ plats',
    'location.error.bothNumberAndCrossStreet': 'Ange antingen ett nummer eller en korsningsgata, inte båda.',
    'location.error.detailsRequired':    'Detaljer krävs för relativ plats.',
    'call.callerName':              'Uppringarens namn',
    'call.callerPhone':             'Uppringarens telefon',
    'call.description':             'Beskrivning',
    'call.outcome':                 'Utfall',
    'call.outcome.none':            'Inget utfall',
    'call.outcome.callerAdvised':   'Uppringaren rådgiven',
    'call.outcome.hoax':            'Falskt larm',
    'call.outcome.accidental':      'Oavsiktligt',
    'call.outcome.otherNoActionsTaken':  'Annat — inga åtgärder',
    'call.outcome.incident_created':     'Uppdrag skapat',
    'call.outcome.attached_to_incident': 'Kopplat till uppdrag',
    'call.outcomeRationale':        'Motivering',
    'call.empty':                   'Inget samtal valt. Skapa ett nytt eller välj från listan.',
    'call.action.endCall':          'Avsluta samtal',
    'call.action.createIncident':   'Skapa uppdrag',
    'call.action.attachToIncident': 'Koppla till uppdrag',
    'call.action.detachFromIncident': 'Koppla bort från uppdrag',
    'call.action.copyLocation':     'Kopiera plats till uppdrag',
    'call.action.copyLocationDisabled': 'Full uppdragsredigering är inte tillgänglig ännu.',
    'call.action.selectIncident':   'Välj uppdrag att koppla till',
    'call.action.cancel':           'Avbryt',
    'call.error.outcomeRequired':   'Ange ett utfall innan samtalet avslutas.',
    'call.error.noIncidents':       'Inga aktiva uppdrag att koppla till.',
    'callList.header.started':      'Startad',
    'callList.header.callerName':   'Uppringare',
    'callList.header.callerPhone':  'Telefon',
    'callList.header.location':     'Plats',
    'callList.header.state':        'Status',
    'callList.header.outcome':      'Utfall',
    'callList.header.dispatcher':   'Operatör',
    'callList.filter.placeholder':  'Filtrera samtal...',
    'callList.empty':               'Inga aktiva samtal.',
    'incidentList.header.created':  'Skapad',
    'incidentList.header.type':     'Typ',
    'incidentList.header.priority': 'Prioritet',
    'incidentList.header.location': 'Plats',
    'incidentList.header.state':    'Status',
    'incidentList.header.linkedCalls': 'Samtal',
    'incidentList.filter.placeholder': 'Filtrera uppdrag...',
    'incidentList.showEnded':       'Visa avslutade',
    'incidentList.empty':           'Inga uppdrag.',
    'incident.state.new':           'Nytt',
    'incident.state.queued':        'Köat',
    'incident.state.active':        'Aktivt',
    'incident.state.monitored':     'Övervakat',
    'incident.state.ended':         'Avslutat',
    'vicinity.banner':              'Visar samtal och uppdrag i närheten',
    'vicinity.clear':               'Rensa filter',
    'ws.disconnected':              'Frånkopplad',
    'ws.reconnecting':              'Återansluter…',
    'primaryWindow.empty':          'Välj ett samtal eller skapa ett nytt.',
    'gis.unavailable.unauthorized': 'Kartan är inte tillgänglig: GIS-servern nekade åtkomsttoken. Kontakta administratören.',
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

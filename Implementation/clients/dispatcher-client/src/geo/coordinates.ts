// Coordinate parsing and formatting utilities for iDispatchX Dispatcher Client.

export function parseCoordinates(input: string): { lat: number; lon: number } | null {
    const s = input.trim();

    const dms = tryParseDMS(s);
    if (dms !== null) return dms;

    const ddm = tryParseDDM(s);
    if (ddm !== null) return ddm;

    const dd = tryParseDD(s);
    return dd;
}

export function validateFinlandBounds(lat: number, lon: number): boolean {
    return lat >= 58.84 && lat <= 70.09 && lon >= 19.08 && lon <= 31.59;
}

export function formatCoordinates(lat: number, lon: number, fmt: 'DD' | 'DDM' | 'DMS'): string {
    const latHem = lat >= 0 ? 'N' : 'S';
    const lonHem = lon >= 0 ? 'E' : 'W';
    const absLat = Math.abs(lat);
    const absLon = Math.abs(lon);

    if (fmt === 'DD') {
        return `${absLat.toFixed(6)}°${latHem} ${absLon.toFixed(6).padStart(10, '0')}°${lonHem}`;
    }

    if (fmt === 'DDM') {
        const latDeg = Math.floor(absLat);
        const latMin = (absLat - latDeg) * 60;
        const lonDeg = Math.floor(absLon);
        const lonMin = (absLon - lonDeg) * 60;
        return (
            `${String(latDeg).padStart(2, '0')}°${latMin.toFixed(4)}′${latHem} ` +
            `${String(lonDeg).padStart(3, '0')}°${lonMin.toFixed(4)}′${lonHem}`
        );
    }

    // DMS
    const latDeg = Math.floor(absLat);
    const latMinTotal = (absLat - latDeg) * 60;
    const latMin = Math.floor(latMinTotal);
    const latSec = (latMinTotal - latMin) * 60;

    const lonDeg = Math.floor(absLon);
    const lonMinTotal = (absLon - lonDeg) * 60;
    const lonMin = Math.floor(lonMinTotal);
    const lonSec = (lonMinTotal - lonMin) * 60;

    return (
        `${String(latDeg).padStart(2, '0')}°${String(latMin).padStart(2, '0')}′${latSec.toFixed(1)}″${latHem} ` +
        `${String(lonDeg).padStart(3, '0')}°${String(lonMin).padStart(2, '0')}′${lonSec.toFixed(1)}″${lonHem}`
    );
}

// ---- internal helpers ----

// Normalise Unicode primes to ASCII equivalents for simpler regex matching.
function normalise(s: string): string {
    return s
        .replace(/′/g, "'")   // prime → apostrophe
        .replace(/″/g, '"');  // double prime → quote
}

function applyDirection(value: number, dir: string): number {
    return dir === 'S' || dir === 'W' ? -value : value;
}

// DMS: degrees° minutes' seconds" [NSEW], optionally NSEW before the number.
function tryParseDMS(raw: string): { lat: number; lon: number } | null {
    const s = normalise(raw);

    // Matches one DMS token with optional leading/trailing direction letter.
    const token =
        '([NSEW])?\\s*' +
        '(\\d+)\\s*°\\s*' +
        '(\\d+)\\s*\'\\s*' +
        '(\\d+(?:\\.\\d+)?)\\s*"\\s*' +
        '([NSEW])?';

    const re = new RegExp(
        `^${token}[,\\s]+${token}$`,
        'i',
    );

    const m = s.match(re);
    if (!m) return null;

    const [, d1pre, d1, m1, s1, d1post, d2pre, d2, m2, s2, d2post] = m;

    const dir1 = (d1pre ?? d1post ?? '').toUpperCase();
    const dir2 = (d2pre ?? d2post ?? '').toUpperCase();

    const val1 = parseInt(d1) + parseInt(m1) / 60 + parseFloat(s1) / 3600;
    const val2 = parseInt(d2) + parseInt(m2) / 60 + parseFloat(s2) / 3600;

    return resolveLatLon(val1, dir1, val2, dir2);
}

// DDM: degrees° minutes' [NSEW], optionally NSEW before the number.
function tryParseDDM(raw: string): { lat: number; lon: number } | null {
    const s = normalise(raw);

    const token =
        '([NSEW])?\\s*' +
        '(\\d+)\\s*°\\s*' +
        '(\\d+(?:\\.\\d+)?)\\s*\'\\s*' +
        '([NSEW])?';

    const re = new RegExp(
        `^${token}[,\\s]+${token}$`,
        'i',
    );

    const m = s.match(re);
    if (!m) return null;

    // Reject if any seconds are present (that would be DMS)
    if (raw.includes('"') || raw.includes('″')) return null;

    const [, d1pre, d1, m1, d1post, d2pre, d2, m2, d2post] = m;

    const dir1 = (d1pre ?? d1post ?? '').toUpperCase();
    const dir2 = (d2pre ?? d2post ?? '').toUpperCase();

    const val1 = parseInt(d1) + parseFloat(m1) / 60;
    const val2 = parseInt(d2) + parseFloat(m2) / 60;

    return resolveLatLon(val1, dir1, val2, dir2);
}

// DD: decimal degrees, with or without N/S/E/W.
function tryParseDD(raw: string): { lat: number; lon: number } | null {
    const s = normalise(raw);

    const token = '([NSEW])?\\s*(\\d+(?:\\.\\d+)?)\\s*°?\\s*([NSEW])?';

    const re = new RegExp(
        `^${token}[,\\s]+${token}$`,
        'i',
    );

    const m = s.match(re);
    if (!m) return null;

    // Reject if degree/minute/second symbols are present
    if (s.includes('°') || s.includes("'") || s.includes('"')) return null;

    const [, d1pre, n1, d1post, d2pre, n2, d2post] = m;

    const dir1 = (d1pre ?? d1post ?? '').toUpperCase();
    const dir2 = (d2pre ?? d2post ?? '').toUpperCase();

    const val1 = parseFloat(n1);
    const val2 = parseFloat(n2);

    if (isNaN(val1) || isNaN(val2)) return null;

    return resolveLatLon(val1, dir1, val2, dir2);
}

function resolveLatLon(
    val1: number,
    dir1: string,
    val2: number,
    dir2: string,
): { lat: number; lon: number } | null {
    // Both directions given explicitly
    if (dir1 && dir2) {
        if ((dir1 === 'N' || dir1 === 'S') && (dir2 === 'E' || dir2 === 'W')) {
            return { lat: applyDirection(val1, dir1), lon: applyDirection(val2, dir2) };
        }
        if ((dir1 === 'E' || dir1 === 'W') && (dir2 === 'N' || dir2 === 'S')) {
            return { lat: applyDirection(val2, dir2), lon: applyDirection(val1, dir1) };
        }
        return null;
    }

    // One direction given — assume standard lat-first order
    if (dir1 || dir2) {
        return { lat: applyDirection(val1, dir1), lon: applyDirection(val2, dir2) };
    }

    // No direction: first = lat, second = lon
    return { lat: val1, lon: val2 };
}

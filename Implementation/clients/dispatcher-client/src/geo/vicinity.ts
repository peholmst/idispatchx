import type { Coordinates } from '../cad/types.ts';

const EARTH_RADIUS_M = 6_371_000;

function toRad(deg: number): number {
    return (deg * Math.PI) / 180;
}

export function haversineDistance(a: Coordinates, b: Coordinates): number {
    const dLat = toRad(b.latitude - a.latitude);
    const dLon = toRad(b.longitude - a.longitude);
    const sinDlat = Math.sin(dLat / 2);
    const sinDlon = Math.sin(dLon / 2);
    const h =
        sinDlat * sinDlat +
        Math.cos(toRad(a.latitude)) * Math.cos(toRad(b.latitude)) * sinDlon * sinDlon;
    return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

export function filterByVicinity<T extends { coordinates?: Coordinates | null }>(
    items: T[],
    center: Coordinates,
    radiusMeters: number,
): T[] {
    return items.filter(
        (item) => item.coordinates != null && haversineDistance(center, item.coordinates) <= radiusMeters,
    );
}

import { describe, it, expect } from 'vitest';
import { haversineDistance, filterByVicinity } from './vicinity.ts';
import type { Coordinates } from '../cad/types.ts';

// Helsinki city center
const HELSINKI: Coordinates = { latitude: 60.169857, longitude: 24.938379 };
// Approx 1 km north of Helsinki center
const NORTH_1KM: Coordinates = { latitude: 60.178858, longitude: 24.938379 };
// Approx 2 km north
const NORTH_2KM: Coordinates = { latitude: 60.187858, longitude: 24.938379 };

describe('haversineDistance', () => {
    it('returns 0 for identical coordinates', () => {
        expect(haversineDistance(HELSINKI, HELSINKI)).toBe(0);
    });

    it('calculates approximately correct distance for ~1 km', () => {
        const dist = haversineDistance(HELSINKI, NORTH_1KM);
        expect(dist).toBeGreaterThan(900);
        expect(dist).toBeLessThan(1100);
    });

    it('calculates approximately correct distance for ~2 km', () => {
        const dist = haversineDistance(HELSINKI, NORTH_2KM);
        expect(dist).toBeGreaterThan(1900);
        expect(dist).toBeLessThan(2100);
    });

    it('is symmetric', () => {
        const d1 = haversineDistance(HELSINKI, NORTH_1KM);
        const d2 = haversineDistance(NORTH_1KM, HELSINKI);
        expect(d1).toBeCloseTo(d2, 5);
    });
});

describe('filterByVicinity', () => {
    const items = [
        { id: 'a', coordinates: HELSINKI },
        { id: 'b', coordinates: NORTH_1KM },
        { id: 'c', coordinates: NORTH_2KM },
        { id: 'd', coordinates: null },
        { id: 'e', coordinates: undefined },
    ];

    it('includes items within radius', () => {
        const result = filterByVicinity(items, HELSINKI, 1500);
        const ids = result.map((i) => i.id);
        expect(ids).toContain('a');
        expect(ids).toContain('b');
    });

    it('excludes items outside radius', () => {
        const result = filterByVicinity(items, HELSINKI, 1500);
        const ids = result.map((i) => i.id);
        expect(ids).not.toContain('c');
    });

    it('excludes items with null coordinates', () => {
        const result = filterByVicinity(items, HELSINKI, 100_000);
        const ids = result.map((i) => i.id);
        expect(ids).not.toContain('d');
        expect(ids).not.toContain('e');
    });

    it('returns all items with coordinates when radius is very large', () => {
        const result = filterByVicinity(items, HELSINKI, 100_000);
        expect(result).toHaveLength(3);
    });

    it('returns empty array when no items are within radius', () => {
        // Tampere center — > 150 km from all Helsinki test items
        const TAMPERE: Coordinates = { latitude: 61.498, longitude: 23.760 };
        const result = filterByVicinity(items, TAMPERE, 100);
        expect(result).toHaveLength(0);
    });
});

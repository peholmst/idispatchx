import { describe, it, expect } from 'vitest';
import { parseCoordinates, validateFinlandBounds, formatCoordinates } from './coordinates.ts';

// ---- parseCoordinates ----

describe('parseCoordinates', () => {
    describe('Decimal Degrees (DD)', () => {
        it('parses space-separated DD', () => {
            expect(parseCoordinates('65.5 25.75')).toEqual({ lat: 65.5, lon: 25.75 });
        });

        it('parses comma-separated DD', () => {
            expect(parseCoordinates('65.5,25.75')).toEqual({ lat: 65.5, lon: 25.75 });
        });

        it('parses DD with trailing direction letters', () => {
            expect(parseCoordinates('65.5N 25.75E')).toEqual({ lat: 65.5, lon: 25.75 });
        });

        it('parses DD with leading direction letters', () => {
            expect(parseCoordinates('N65.5 E25.75')).toEqual({ lat: 65.5, lon: 25.75 });
        });

        it('applies S/W to produce negative values', () => {
            expect(parseCoordinates('S65.5 W25.75')).toEqual({ lat: -65.5, lon: -25.75 });
        });

        it('accepts integer degrees', () => {
            expect(parseCoordinates('65 25')).toEqual({ lat: 65, lon: 25 });
        });
    });

    describe('Degrees Decimal Minutes (DDM)', () => {
        it('parses DDM with ASCII prime', () => {
            expect(parseCoordinates("65°30.0' 025°45.0'")).toEqual({ lat: 65.5, lon: 25.75 });
        });

        it('parses DDM with Unicode prime', () => {
            expect(parseCoordinates('65°30.0′ 025°45.0′')).toEqual({ lat: 65.5, lon: 25.75 });
        });

        it('parses DDM with leading direction letters', () => {
            expect(parseCoordinates("N65°30.0' E025°45.0'")).toEqual({ lat: 65.5, lon: 25.75 });
        });

        it('parses DDM with trailing direction letters', () => {
            expect(parseCoordinates("65°30.0'N 025°45.0'E")).toEqual({ lat: 65.5, lon: 25.75 });
        });
    });

    describe('Degrees Minutes Seconds (DMS)', () => {
        it('parses DMS with ASCII quote', () => {
            expect(parseCoordinates('65°30\'00.0" 025°45\'00.0"')).toEqual({ lat: 65.5, lon: 25.75 });
        });

        it('parses DMS with Unicode double prime', () => {
            expect(parseCoordinates('65°30′00.0″ 025°45′00.0″')).toEqual({ lat: 65.5, lon: 25.75 });
        });

        it('parses DMS with leading direction letters', () => {
            expect(parseCoordinates('N65°30\'00.0" E025°45\'00.0"')).toEqual({ lat: 65.5, lon: 25.75 });
        });

        it('parses DMS with fractional seconds', () => {
            // 65°00'36.0" = 65 + 0/60 + 36/3600 = 65.01
            const result = parseCoordinates('65°00\'36.0" 025°00\'00.0"');
            expect(result).not.toBeNull();
            expect(result!.lat).toBeCloseTo(65.01, 5);
            expect(result!.lon).toBeCloseTo(25.0, 5);
        });
    });

    describe('invalid inputs', () => {
        it('returns null for empty string', () => {
            expect(parseCoordinates('')).toBeNull();
        });

        it('returns null for non-coordinate text', () => {
            expect(parseCoordinates('Helsinki')).toBeNull();
        });

        it('returns null for a single number', () => {
            expect(parseCoordinates('65.5')).toBeNull();
        });
    });
});

// ---- validateFinlandBounds ----

describe('validateFinlandBounds', () => {
    it('accepts a coordinate in the center of Finland', () => {
        expect(validateFinlandBounds(65.0, 25.0)).toBe(true);
    });

    it('accepts coordinates at the minimum boundary', () => {
        expect(validateFinlandBounds(58.84, 19.08)).toBe(true);
    });

    it('accepts coordinates at the maximum boundary', () => {
        expect(validateFinlandBounds(70.09, 31.59)).toBe(true);
    });

    it('rejects latitude too far south', () => {
        expect(validateFinlandBounds(58.83, 25.0)).toBe(false);
    });

    it('rejects latitude too far north', () => {
        expect(validateFinlandBounds(70.10, 25.0)).toBe(false);
    });

    it('rejects longitude too far west', () => {
        expect(validateFinlandBounds(65.0, 19.07)).toBe(false);
    });

    it('rejects longitude too far east', () => {
        expect(validateFinlandBounds(65.0, 31.60)).toBe(false);
    });
});

// ---- formatCoordinates ----

describe('formatCoordinates', () => {
    describe('DD format', () => {
        it('formats north-east coordinate', () => {
            expect(formatCoordinates(65.5, 25.75, 'DD')).toBe('65.500000°N 025.750000°E');
        });

        it('uses S/W for negative values', () => {
            expect(formatCoordinates(-65.5, -25.75, 'DD')).toBe('65.500000°S 025.750000°W');
        });

        it('zero-pads longitude to 10 characters', () => {
            expect(formatCoordinates(65.0, 9.0, 'DD')).toBe('65.000000°N 009.000000°E');
        });
    });

    describe('DDM format', () => {
        it('formats whole-degree values', () => {
            expect(formatCoordinates(65.0, 25.0, 'DDM')).toBe('65°0.0000′N 025°0.0000′E');
        });

        it('converts fractional degrees to minutes', () => {
            expect(formatCoordinates(65.5, 25.75, 'DDM')).toBe('65°30.0000′N 025°45.0000′E');
        });
    });

    describe('DMS format', () => {
        it('formats whole-degree values', () => {
            expect(formatCoordinates(65.0, 25.0, 'DMS')).toBe('65°00′0.0″N 025°00′0.0″E');
        });

        it('converts fractional degrees to minutes and seconds', () => {
            expect(formatCoordinates(65.5, 25.75, 'DMS')).toBe('65°30′0.0″N 025°45′0.0″E');
        });

        it('uses S/W for negative values', () => {
            expect(formatCoordinates(-1.5, -10.25, 'DMS')).toBe('01°30′0.0″S 010°15′0.0″W');
        });
    });
});

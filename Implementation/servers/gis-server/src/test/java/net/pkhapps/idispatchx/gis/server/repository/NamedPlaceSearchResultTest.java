package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.Language;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NamedPlaceSearchResultTest {

    @Test
    void constructor_withValidData_createsResult() {
        var name = MultilingualName.of(Language.of("fi"), "Helsinki");
        var coordinates = Coordinates.Epsg4326.of(60.1699, 24.9384);

        var result = new NamedPlaceSearchResult(
                12345L,
                name,
                100,
                null,
                coordinates,
                0.85
        );

        assertEquals(12345L, result.karttanimiId());
        assertEquals(name, result.name());
        assertEquals(100, result.placeClass());
        assertNull(result.municipality());
        assertEquals(coordinates, result.coordinates());
        assertEquals(0.85, result.similarityScore());
    }

    @Test
    void constructor_withMunicipality_createsResult() {
        var name = MultilingualName.of(Language.of("fi"), "Munkkiniemi");
        var municipalityName = MultilingualName.of(Language.of("fi"), "Helsinki");
        var municipality = Municipality.of(MunicipalityCode.of("091"), municipalityName);
        var coordinates = Coordinates.Epsg4326.of(60.1999, 24.8799);

        var result = new NamedPlaceSearchResult(
                12345L,
                name,
                200,
                municipality,
                coordinates,
                0.95
        );

        assertEquals(municipality, result.municipality());
    }

    @Test
    void constructor_nullName_throws() {
        var coordinates = Coordinates.Epsg4326.of(60.1699, 24.9384);

        assertThrows(NullPointerException.class, () ->
                new NamedPlaceSearchResult(12345L, null, 100, null, coordinates, 0.85)
        );
    }

    @Test
    void constructor_nullCoordinates_throws() {
        var name = MultilingualName.of(Language.of("fi"), "Helsinki");

        assertThrows(NullPointerException.class, () ->
                new NamedPlaceSearchResult(12345L, name, 100, null, null, 0.85)
        );
    }

    @Test
    void constructor_negativeSimilarityScore_throws() {
        var name = MultilingualName.of(Language.of("fi"), "Helsinki");
        var coordinates = Coordinates.Epsg4326.of(60.1699, 24.9384);

        var exception = assertThrows(IllegalArgumentException.class, () ->
                new NamedPlaceSearchResult(12345L, name, 100, null, coordinates, -0.1)
        );

        assertTrue(exception.getMessage().contains("similarityScore"));
        assertTrue(exception.getMessage().contains("0.0 and 1.0"));
    }

    @Test
    void constructor_similarityScoreAboveOne_throws() {
        var name = MultilingualName.of(Language.of("fi"), "Helsinki");
        var coordinates = Coordinates.Epsg4326.of(60.1699, 24.9384);

        var exception = assertThrows(IllegalArgumentException.class, () ->
                new NamedPlaceSearchResult(12345L, name, 100, null, coordinates, 1.1)
        );

        assertTrue(exception.getMessage().contains("similarityScore"));
        assertTrue(exception.getMessage().contains("0.0 and 1.0"));
    }

    @Test
    void constructor_similarityScoreAtBoundaries_succeeds() {
        var name = MultilingualName.of(Language.of("fi"), "Helsinki");
        var coordinates = Coordinates.Epsg4326.of(60.1699, 24.9384);

        var resultZero = new NamedPlaceSearchResult(12345L, name, 100, null, coordinates, 0.0);
        assertEquals(0.0, resultZero.similarityScore());

        var resultOne = new NamedPlaceSearchResult(12345L, name, 100, null, coordinates, 1.0);
        assertEquals(1.0, resultOne.similarityScore());
    }
}

package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.Language;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddressSearchResultTest {

    private static final MultilingualName STREET_NAME = MultilingualName.of(
            Language.of("fi"), "Mannerheimintie");
    private static final Coordinates.Epsg4326 COORDINATES = Coordinates.Epsg4326.of(60.1699, 24.9384);
    private static final Municipality MUNICIPALITY = Municipality.of(
            MunicipalityCode.of("091"),
            MultilingualName.of(Language.of("fi"), "Helsinki"));

    @Test
    void constructor_withValidValues_createsResult() {
        var result = new AddressSearchResult(
                1L, "12", STREET_NAME, MUNICIPALITY, COORDINATES, 0.85);

        assertEquals(1L, result.id());
        assertEquals("12", result.number());
        assertEquals(STREET_NAME, result.streetName());
        assertEquals(MUNICIPALITY, result.municipality());
        assertEquals(COORDINATES, result.coordinates());
        assertEquals(0.85, result.similarityScore());
    }

    @Test
    void constructor_withNullNumber_createsResult() {
        var result = new AddressSearchResult(
                1L, null, STREET_NAME, MUNICIPALITY, COORDINATES, 0.5);

        assertNull(result.number());
    }

    @Test
    void constructor_withNullMunicipality_createsResult() {
        var result = new AddressSearchResult(
                1L, "12", STREET_NAME, null, COORDINATES, 0.5);

        assertNull(result.municipality());
    }

    @Test
    void constructor_withNullStreetName_throws() {
        assertThrows(NullPointerException.class, () ->
                new AddressSearchResult(1L, "12", null, MUNICIPALITY, COORDINATES, 0.5));
    }

    @Test
    void constructor_withNullCoordinates_throws() {
        assertThrows(NullPointerException.class, () ->
                new AddressSearchResult(1L, "12", STREET_NAME, MUNICIPALITY, null, 0.5));
    }

    @Test
    void constructor_withNegativeSimilarityScore_throws() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                new AddressSearchResult(1L, "12", STREET_NAME, MUNICIPALITY, COORDINATES, -0.1));

        assertTrue(exception.getMessage().contains("similarityScore"));
    }

    @Test
    void constructor_withSimilarityScoreAboveOne_throws() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                new AddressSearchResult(1L, "12", STREET_NAME, MUNICIPALITY, COORDINATES, 1.1));

        assertTrue(exception.getMessage().contains("similarityScore"));
    }

    @Test
    void constructor_withZeroSimilarityScore_createsResult() {
        var result = new AddressSearchResult(
                1L, "12", STREET_NAME, MUNICIPALITY, COORDINATES, 0.0);

        assertEquals(0.0, result.similarityScore());
    }

    @Test
    void constructor_withOneSimilarityScore_createsResult() {
        var result = new AddressSearchResult(
                1L, "12", STREET_NAME, MUNICIPALITY, COORDINATES, 1.0);

        assertEquals(1.0, result.similarityScore());
    }
}

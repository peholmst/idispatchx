package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.Language;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntersectionSearchResultTest {

    private static final MultilingualName ROAD_A = MultilingualName.of(
            Language.of("fi"), "Mannerheimintie");
    private static final MultilingualName ROAD_B = MultilingualName.of(
            Language.of("fi"), "Aleksanterinkatu");
    private static final Coordinates.Epsg4326 COORDINATES = Coordinates.Epsg4326.of(60.1699, 24.9384);
    private static final Municipality MUNICIPALITY = Municipality.of(
            MunicipalityCode.of("091"),
            MultilingualName.of(Language.of("fi"), "Helsinki"));

    @Test
    void constructor_withValidValues_createsResult() {
        var result = new IntersectionSearchResult(
                ROAD_A, ROAD_B, MUNICIPALITY, COORDINATES, 0.85);

        assertEquals(ROAD_A, result.roadA());
        assertEquals(ROAD_B, result.roadB());
        assertEquals(MUNICIPALITY, result.municipality());
        assertEquals(COORDINATES, result.coordinates());
        assertEquals(0.85, result.similarityScore());
    }

    @Test
    void constructor_withNullMunicipality_createsResult() {
        var result = new IntersectionSearchResult(
                ROAD_A, ROAD_B, null, COORDINATES, 0.5);

        assertNull(result.municipality());
    }

    @Test
    void constructor_withNullRoadA_throws() {
        assertThrows(NullPointerException.class, () ->
                new IntersectionSearchResult(null, ROAD_B, MUNICIPALITY, COORDINATES, 0.5));
    }

    @Test
    void constructor_withNullRoadB_throws() {
        assertThrows(NullPointerException.class, () ->
                new IntersectionSearchResult(ROAD_A, null, MUNICIPALITY, COORDINATES, 0.5));
    }

    @Test
    void constructor_withNullCoordinates_throws() {
        assertThrows(NullPointerException.class, () ->
                new IntersectionSearchResult(ROAD_A, ROAD_B, MUNICIPALITY, null, 0.5));
    }

    @Test
    void constructor_withNegativeSimilarityScore_throws() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                new IntersectionSearchResult(ROAD_A, ROAD_B, MUNICIPALITY, COORDINATES, -0.1));

        assertTrue(exception.getMessage().contains("similarityScore"));
    }

    @Test
    void constructor_withSimilarityScoreAboveOne_throws() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                new IntersectionSearchResult(ROAD_A, ROAD_B, MUNICIPALITY, COORDINATES, 1.1));

        assertTrue(exception.getMessage().contains("similarityScore"));
    }

    @Test
    void constructor_withZeroSimilarityScore_createsResult() {
        var result = new IntersectionSearchResult(
                ROAD_A, ROAD_B, MUNICIPALITY, COORDINATES, 0.0);

        assertEquals(0.0, result.similarityScore());
    }

    @Test
    void constructor_withOneSimilarityScore_createsResult() {
        var result = new IntersectionSearchResult(
                ROAD_A, ROAD_B, MUNICIPALITY, COORDINATES, 1.0);

        assertEquals(1.0, result.similarityScore());
    }

    @Test
    void constructor_withSameRoads_createsResult() {
        // Although unlikely, the system should allow this (self-intersection)
        var result = new IntersectionSearchResult(
                ROAD_A, ROAD_A, MUNICIPALITY, COORDINATES, 0.5);

        assertEquals(ROAD_A, result.roadA());
        assertEquals(ROAD_A, result.roadB());
    }
}

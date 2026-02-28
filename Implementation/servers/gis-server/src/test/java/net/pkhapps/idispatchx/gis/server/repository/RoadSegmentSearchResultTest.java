package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.Language;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoadSegmentSearchResultTest {

    private static final MultilingualName ROAD_NAME = MultilingualName.of(
            Language.of("fi"), "Mannerheimintie");
    private static final Municipality MUNICIPALITY = Municipality.of(
            MunicipalityCode.of("091"),
            MultilingualName.of(Language.of("fi"), "Helsinki"));

    @Test
    void constructor_withValidValues_createsResult() {
        var result = new RoadSegmentSearchResult(
                1L, ROAD_NAME, MUNICIPALITY, 1, 99, 2, 100, 0.85);

        assertEquals(1L, result.id());
        assertEquals(ROAD_NAME, result.roadName());
        assertEquals(MUNICIPALITY, result.municipality());
        assertEquals(1, result.minAddressLeft());
        assertEquals(99, result.maxAddressLeft());
        assertEquals(2, result.minAddressRight());
        assertEquals(100, result.maxAddressRight());
        assertEquals(0.85, result.similarityScore());
    }

    @Test
    void constructor_withNullMunicipality_createsResult() {
        var result = new RoadSegmentSearchResult(
                1L, ROAD_NAME, null, 1, 99, 2, 100, 0.5);

        assertNull(result.municipality());
    }

    @Test
    void constructor_withNullAddressRanges_createsResult() {
        var result = new RoadSegmentSearchResult(
                1L, ROAD_NAME, MUNICIPALITY, null, null, null, null, 0.5);

        assertNull(result.minAddressLeft());
        assertNull(result.maxAddressLeft());
        assertNull(result.minAddressRight());
        assertNull(result.maxAddressRight());
    }

    @Test
    void constructor_withNullRoadName_throws() {
        assertThrows(NullPointerException.class, () ->
                new RoadSegmentSearchResult(1L, null, MUNICIPALITY, 1, 99, 2, 100, 0.5));
    }

    @Test
    void constructor_withNegativeSimilarityScore_throws() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                new RoadSegmentSearchResult(1L, ROAD_NAME, MUNICIPALITY, 1, 99, 2, 100, -0.1));

        assertTrue(exception.getMessage().contains("similarityScore"));
    }

    @Test
    void constructor_withSimilarityScoreAboveOne_throws() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                new RoadSegmentSearchResult(1L, ROAD_NAME, MUNICIPALITY, 1, 99, 2, 100, 1.1));

        assertTrue(exception.getMessage().contains("similarityScore"));
    }

    @Test
    void constructor_withZeroSimilarityScore_createsResult() {
        var result = new RoadSegmentSearchResult(
                1L, ROAD_NAME, MUNICIPALITY, 1, 99, 2, 100, 0.0);

        assertEquals(0.0, result.similarityScore());
    }

    @Test
    void constructor_withOneSimilarityScore_createsResult() {
        var result = new RoadSegmentSearchResult(
                1L, ROAD_NAME, MUNICIPALITY, 1, 99, 2, 100, 1.0);

        assertEquals(1.0, result.similarityScore());
    }
}

package net.pkhapps.idispatchx.common.domain.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoordinatesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // === EPSG:4326 Construction Tests ===

    @Test
    void epsg4326_of_validCoordinates_createsInstance() {
        var coords = Coordinates.Epsg4326.of(60.169857, 24.938379);
        assertEquals(60.169857, coords.latitude());
        assertEquals(24.938379, coords.longitude());
    }

    @Test
    void epsg4326_of_minBounds_createsInstance() {
        var coords = Coordinates.Epsg4326.of(58.84, 19.08);
        assertEquals(58.84, coords.latitude());
        assertEquals(19.08, coords.longitude());
    }

    @Test
    void epsg4326_of_maxBounds_createsInstance() {
        var coords = Coordinates.Epsg4326.of(70.09, 31.59);
        assertEquals(70.09, coords.latitude());
        assertEquals(31.59, coords.longitude());
    }

    @Test
    void epsg4326_of_zeroPrecision_createsInstance() {
        var coords = Coordinates.Epsg4326.of(60.0, 25.0);
        assertEquals(60.0, coords.latitude());
        assertEquals(25.0, coords.longitude());
    }

    @Test
    void epsg4326_of_sixDecimalPlaces_createsInstance() {
        var coords = Coordinates.Epsg4326.of(60.123456, 24.654321);
        assertEquals(60.123456, coords.latitude());
        assertEquals(24.654321, coords.longitude());
    }

    // === EPSG:4326 Precision Validation Tests ===

    @Test
    void epsg4326_of_sevenDecimalPlacesLatitude_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg4326.of(60.1234567, 24.0));
        assertTrue(exception.getMessage().contains("latitude"));
        assertTrue(exception.getMessage().contains("decimal places"));
    }

    @Test
    void epsg4326_of_sevenDecimalPlacesLongitude_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg4326.of(60.0, 24.1234567));
        assertTrue(exception.getMessage().contains("longitude"));
        assertTrue(exception.getMessage().contains("decimal places"));
    }

    // === EPSG:4326 Bounds Validation Tests ===

    @Test
    void epsg4326_of_latitudeBelowMin_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg4326.of(58.83, 24.0));
        assertTrue(exception.getMessage().contains("latitude"));
        assertTrue(exception.getMessage().contains("between"));
    }

    @Test
    void epsg4326_of_latitudeAboveMax_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg4326.of(70.1, 24.0));
        assertTrue(exception.getMessage().contains("latitude"));
        assertTrue(exception.getMessage().contains("between"));
    }

    @Test
    void epsg4326_of_longitudeBelowMin_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg4326.of(60.0, 19.07));
        assertTrue(exception.getMessage().contains("longitude"));
        assertTrue(exception.getMessage().contains("between"));
    }

    @Test
    void epsg4326_of_longitudeAboveMax_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg4326.of(60.0, 31.6));
        assertTrue(exception.getMessage().contains("longitude"));
        assertTrue(exception.getMessage().contains("between"));
    }

    // === EPSG:4326 Finite Validation Tests ===

    @Test
    void epsg4326_of_nanLatitude_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg4326.of(Double.NaN, 24.0));
        assertTrue(exception.getMessage().contains("latitude"));
        assertTrue(exception.getMessage().contains("finite"));
    }

    @Test
    void epsg4326_of_nanLongitude_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg4326.of(60.0, Double.NaN));
        assertTrue(exception.getMessage().contains("longitude"));
        assertTrue(exception.getMessage().contains("finite"));
    }

    @Test
    void epsg4326_of_positiveInfinityLatitude_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg4326.of(Double.POSITIVE_INFINITY, 24.0));
        assertTrue(exception.getMessage().contains("latitude"));
        assertTrue(exception.getMessage().contains("finite"));
    }

    @Test
    void epsg4326_of_negativeInfinityLongitude_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg4326.of(60.0, Double.NEGATIVE_INFINITY));
        assertTrue(exception.getMessage().contains("longitude"));
        assertTrue(exception.getMessage().contains("finite"));
    }

    // === EPSG:4326 Equality Tests ===

    @Test
    void epsg4326_equals_sameValues_returnsTrue() {
        assertEquals(
                Coordinates.Epsg4326.of(60.169857, 24.938379),
                Coordinates.Epsg4326.of(60.169857, 24.938379));
    }

    @Test
    void epsg4326_equals_differentValues_returnsFalse() {
        assertNotEquals(
                Coordinates.Epsg4326.of(60.169857, 24.938379),
                Coordinates.Epsg4326.of(61.0, 24.938379));
    }

    @Test
    void epsg4326_hashCode_sameValues_sameHashCode() {
        assertEquals(
                Coordinates.Epsg4326.of(60.169857, 24.938379).hashCode(),
                Coordinates.Epsg4326.of(60.169857, 24.938379).hashCode());
    }

    // === EPSG:4326 toString Tests ===

    @Test
    void epsg4326_toString_correctFormat() {
        var coords = Coordinates.Epsg4326.of(60.169857, 24.938379);
        assertEquals("EPSG:4326[60.169857, 24.938379]", coords.toString());
    }

    // === EPSG:3067 Construction Tests ===

    @Test
    void epsg3067_of_validCoordinates_createsInstance() {
        var coords = Coordinates.Epsg3067.of(385784.0, 6672298.0);
        assertEquals(385784.0, coords.easting());
        assertEquals(6672298.0, coords.northing());
    }

    @Test
    void epsg3067_of_minBounds_createsInstance() {
        var coords = Coordinates.Epsg3067.of(43547.79, 6522236.87);
        assertEquals(43547.79, coords.easting());
        assertEquals(6522236.87, coords.northing());
    }

    @Test
    void epsg3067_of_maxBounds_createsInstance() {
        var coords = Coordinates.Epsg3067.of(764796.72, 7795461.19);
        assertEquals(764796.72, coords.easting());
        assertEquals(7795461.19, coords.northing());
    }

    // === EPSG:3067 Bounds Validation Tests ===

    @Test
    void epsg3067_of_eastingBelowMin_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg3067.of(43547.78, 6672298.0));
        assertTrue(exception.getMessage().contains("easting"));
        assertTrue(exception.getMessage().contains("between"));
    }

    @Test
    void epsg3067_of_eastingAboveMax_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg3067.of(764796.73, 6672298.0));
        assertTrue(exception.getMessage().contains("easting"));
        assertTrue(exception.getMessage().contains("between"));
    }

    @Test
    void epsg3067_of_northingBelowMin_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg3067.of(385784.0, 6522236.86));
        assertTrue(exception.getMessage().contains("northing"));
        assertTrue(exception.getMessage().contains("between"));
    }

    @Test
    void epsg3067_of_northingAboveMax_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg3067.of(385784.0, 7795461.2));
        assertTrue(exception.getMessage().contains("northing"));
        assertTrue(exception.getMessage().contains("between"));
    }

    // === EPSG:3067 Finite Validation Tests ===

    @Test
    void epsg3067_of_nanEasting_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg3067.of(Double.NaN, 6672298.0));
        assertTrue(exception.getMessage().contains("easting"));
        assertTrue(exception.getMessage().contains("finite"));
    }

    @Test
    void epsg3067_of_nanNorthing_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg3067.of(385784.0, Double.NaN));
        assertTrue(exception.getMessage().contains("northing"));
        assertTrue(exception.getMessage().contains("finite"));
    }

    @Test
    void epsg3067_of_positiveInfinityEasting_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg3067.of(Double.POSITIVE_INFINITY, 6672298.0));
        assertTrue(exception.getMessage().contains("easting"));
        assertTrue(exception.getMessage().contains("finite"));
    }

    @Test
    void epsg3067_of_negativeInfinityNorthing_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Coordinates.Epsg3067.of(385784.0, Double.NEGATIVE_INFINITY));
        assertTrue(exception.getMessage().contains("northing"));
        assertTrue(exception.getMessage().contains("finite"));
    }

    // === EPSG:3067 Equality Tests ===

    @Test
    void epsg3067_equals_sameValues_returnsTrue() {
        assertEquals(
                Coordinates.Epsg3067.of(385784.0, 6672298.0),
                Coordinates.Epsg3067.of(385784.0, 6672298.0));
    }

    @Test
    void epsg3067_equals_differentValues_returnsFalse() {
        assertNotEquals(
                Coordinates.Epsg3067.of(385784.0, 6672298.0),
                Coordinates.Epsg3067.of(385784.0, 6672299.0));
    }

    @Test
    void epsg3067_hashCode_sameValues_sameHashCode() {
        assertEquals(
                Coordinates.Epsg3067.of(385784.0, 6672298.0).hashCode(),
                Coordinates.Epsg3067.of(385784.0, 6672298.0).hashCode());
    }

    // === EPSG:3067 toString Tests ===

    @Test
    void epsg3067_toString_correctFormat() {
        var coords = Coordinates.Epsg3067.of(385784.0, 6672298.0);
        assertEquals("EPSG:3067[385784.0, 6672298.0]", coords.toString());
    }

    // === Sealed Type Tests ===

    @Test
    void sealedType_epsg4326_isCoordinates() {
        Coordinates coords = Coordinates.Epsg4326.of(60.169857, 24.938379);
        assertInstanceOf(Coordinates.Epsg4326.class, coords);
    }

    @Test
    void sealedType_epsg3067_isCoordinates() {
        Coordinates coords = Coordinates.Epsg3067.of(385784.0, 6672298.0);
        assertInstanceOf(Coordinates.Epsg3067.class, coords);
    }

    @Test
    void sealedType_exhaustivePatternMatching() {
        Coordinates wgs84 = Coordinates.Epsg4326.of(60.169857, 24.938379);
        Coordinates euref = Coordinates.Epsg3067.of(385784.0, 6672298.0);

        var wgs84Result = switch (wgs84) {
            case Coordinates.Epsg4326 c -> "WGS84: " + c.latitude() + ", " + c.longitude();
            case Coordinates.Epsg3067 c -> "EUREF: " + c.easting() + ", " + c.northing();
        };
        assertEquals("WGS84: 60.169857, 24.938379", wgs84Result);

        var eurefResult = switch (euref) {
            case Coordinates.Epsg4326 c -> "WGS84: " + c.latitude() + ", " + c.longitude();
            case Coordinates.Epsg3067 c -> "EUREF: " + c.easting() + ", " + c.northing();
        };
        assertEquals("EUREF: 385784.0, 6672298.0", eurefResult);
    }

    // === Jackson Serialization Tests ===

    @Test
    void jackson_serializeEpsg4326_correctJson() throws JsonProcessingException {
        var coords = Coordinates.Epsg4326.of(60.169857, 24.938379);
        var json = objectMapper.writeValueAsString(coords);
        assertTrue(json.contains("\"crs\":\"EPSG:4326\""));
        assertTrue(json.contains("\"latitude\":60.169857"));
        assertTrue(json.contains("\"longitude\":24.938379"));
    }

    @Test
    void jackson_serializeEpsg3067_correctJson() throws JsonProcessingException {
        var coords = Coordinates.Epsg3067.of(385784.0, 6672298.0);
        var json = objectMapper.writeValueAsString(coords);
        assertTrue(json.contains("\"crs\":\"EPSG:3067\""));
        assertTrue(json.contains("\"easting\":385784.0"));
        assertTrue(json.contains("\"northing\":6672298.0"));
    }

    @Test
    void jackson_deserializeEpsg4326_correctObject() throws JsonProcessingException {
        var json = """
                {"crs":"EPSG:4326","latitude":60.169857,"longitude":24.938379}""";
        var coords = objectMapper.readValue(json, Coordinates.class);
        assertInstanceOf(Coordinates.Epsg4326.class, coords);
        var epsg4326 = (Coordinates.Epsg4326) coords;
        assertEquals(60.169857, epsg4326.latitude());
        assertEquals(24.938379, epsg4326.longitude());
    }

    @Test
    void jackson_deserializeEpsg3067_correctObject() throws JsonProcessingException {
        var json = """
                {"crs":"EPSG:3067","easting":385784.0,"northing":6672298.0}""";
        var coords = objectMapper.readValue(json, Coordinates.class);
        assertInstanceOf(Coordinates.Epsg3067.class, coords);
        var epsg3067 = (Coordinates.Epsg3067) coords;
        assertEquals(385784.0, epsg3067.easting());
        assertEquals(6672298.0, epsg3067.northing());
    }

    @Test
    void jackson_roundTripEpsg4326_preservesData() throws JsonProcessingException {
        var original = Coordinates.Epsg4326.of(60.169857, 24.938379);
        var json = objectMapper.writeValueAsString(original);
        var restored = objectMapper.readValue(json, Coordinates.class);
        assertEquals(original, restored);
    }

    @Test
    void jackson_roundTripEpsg3067_preservesData() throws JsonProcessingException {
        var original = Coordinates.Epsg3067.of(385784.0, 6672298.0);
        var json = objectMapper.writeValueAsString(original);
        var restored = objectMapper.readValue(json, Coordinates.class);
        assertEquals(original, restored);
    }

    @Test
    void jackson_deserializeEpsg4326_asConcreteType_correctObject() throws JsonProcessingException {
        var json = """
                {"crs":"EPSG:4326","latitude":60.169857,"longitude":24.938379}""";
        var coords = objectMapper.readValue(json, Coordinates.Epsg4326.class);
        assertEquals(60.169857, coords.latitude());
        assertEquals(24.938379, coords.longitude());
    }

    @Test
    void jackson_deserializeEpsg3067_asConcreteType_correctObject() throws JsonProcessingException {
        var json = """
                {"crs":"EPSG:3067","easting":385784.0,"northing":6672298.0}""";
        var coords = objectMapper.readValue(json, Coordinates.Epsg3067.class);
        assertEquals(385784.0, coords.easting());
        assertEquals(6672298.0, coords.northing());
    }

    // === Jackson Error Cases ===

    @Test
    void jackson_deserializeOutOfBoundsLatitude_throwsException() {
        var json = """
                {"crs":"EPSG:4326","latitude":50.0,"longitude":24.0}""";
        assertThrows(ValueInstantiationException.class,
                () -> objectMapper.readValue(json, Coordinates.class));
    }

    @Test
    void jackson_deserializePrecisionViolation_throwsException() {
        var json = """
                {"crs":"EPSG:4326","latitude":60.1234567,"longitude":24.0}""";
        assertThrows(ValueInstantiationException.class,
                () -> objectMapper.readValue(json, Coordinates.class));
    }

    @Test
    void jackson_deserializeOutOfBoundsEasting_throwsException() {
        var json = """
                {"crs":"EPSG:3067","easting":0.0,"northing":6672298.0}""";
        assertThrows(ValueInstantiationException.class,
                () -> objectMapper.readValue(json, Coordinates.class));
    }

    @Test
    void jackson_deserializeNonFiniteEasting_throwsException() {
        var json = """
                {"crs":"EPSG:3067","easting":"NaN","northing":6672298.0}""";
        assertThrows(Exception.class,
                () -> objectMapper.readValue(json, Coordinates.class));
    }

    @Test
    void jackson_deserializeInvalidCrs_throwsException() {
        var json = """
                {"crs":"EPSG:9999","latitude":60.0,"longitude":24.0}""";
        assertThrows(Exception.class,
                () -> objectMapper.readValue(json, Coordinates.class));
    }
}

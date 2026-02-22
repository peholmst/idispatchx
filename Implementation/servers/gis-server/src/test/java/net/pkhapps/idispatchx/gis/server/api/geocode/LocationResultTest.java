package net.pkhapps.idispatchx.gis.server.api.geocode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.Language;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocationResultTest {

    private static final Language FINNISH = Language.of("fi");
    private static final Language SWEDISH = Language.of("sv");

    private static final MultilingualName STREET_NAME = MultilingualName.of(
            Map.of(FINNISH, "Mannerheimintie", SWEDISH, "Mannerheimvagen"));
    private static final MultilingualName STREET_NAME_2 = MultilingualName.of(
            Map.of(FINNISH, "Kaivokatu", SWEDISH, "Brunnsgatan"));
    private static final MultilingualName PLACE_NAME = MultilingualName.of(
            Map.of(FINNISH, "Mannerheiminaukio", SWEDISH, "Mannerheimplatsen"));
    private static final MultilingualName HELSINKI_NAME = MultilingualName.of(
            Map.of(FINNISH, "Helsinki", SWEDISH, "Helsingfors"));
    private static final Municipality HELSINKI = Municipality.of(
            MunicipalityCode.of("091"), HELSINKI_NAME);
    private static final Coordinates.Epsg4326 COORDS = Coordinates.Epsg4326.of(60.169857, 24.938379);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // === AddressResult Tests ===

    @Test
    void addressResult_validParameters_createsInstance() {
        var result = new AddressResult(STREET_NAME, "1", HELSINKI, COORDS, AddressSource.ADDRESS_POINT);

        assertEquals(STREET_NAME, result.name());
        assertEquals("1", result.number());
        assertEquals(HELSINKI, result.municipality());
        assertEquals(COORDS, result.coordinates());
        assertEquals(AddressSource.ADDRESS_POINT, result.source());
    }

    @Test
    void addressResult_nullName_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new AddressResult(null, "1", HELSINKI, COORDS, AddressSource.ADDRESS_POINT));
    }

    @Test
    void addressResult_emptyName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new AddressResult(MultilingualName.empty(), "1", HELSINKI, COORDS, AddressSource.ADDRESS_POINT));
    }

    @Test
    void addressResult_nullNumber_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new AddressResult(STREET_NAME, null, HELSINKI, COORDS, AddressSource.ADDRESS_POINT));
    }

    @Test
    void addressResult_blankNumber_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new AddressResult(STREET_NAME, "  ", HELSINKI, COORDS, AddressSource.ADDRESS_POINT));
    }

    @Test
    void addressResult_nullMunicipality_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new AddressResult(STREET_NAME, "1", null, COORDS, AddressSource.ADDRESS_POINT));
    }

    @Test
    void addressResult_nullCoordinates_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new AddressResult(STREET_NAME, "1", HELSINKI, null, AddressSource.ADDRESS_POINT));
    }

    @Test
    void addressResult_nullSource_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new AddressResult(STREET_NAME, "1", HELSINKI, COORDS, null));
    }

    // === PlaceResult Tests ===

    @Test
    void placeResult_validParameters_createsInstance() {
        var result = new PlaceResult(PLACE_NAME, 48111, HELSINKI, COORDS);

        assertEquals(PLACE_NAME, result.name());
        assertEquals(48111, result.placeClass());
        assertEquals(HELSINKI, result.municipality());
        assertEquals(COORDS, result.coordinates());
    }

    @Test
    void placeResult_nullName_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new PlaceResult(null, 48111, HELSINKI, COORDS));
    }

    @Test
    void placeResult_emptyName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlaceResult(MultilingualName.empty(), 48111, HELSINKI, COORDS));
    }

    @Test
    void placeResult_negativePlaceClass_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlaceResult(PLACE_NAME, -1, HELSINKI, COORDS));
    }

    @Test
    void placeResult_zeroPlaceClass_createsInstance() {
        var result = new PlaceResult(PLACE_NAME, 0, HELSINKI, COORDS);
        assertEquals(0, result.placeClass());
    }

    @Test
    void placeResult_nullMunicipality_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new PlaceResult(PLACE_NAME, 48111, null, COORDS));
    }

    @Test
    void placeResult_nullCoordinates_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new PlaceResult(PLACE_NAME, 48111, HELSINKI, null));
    }

    // === IntersectionResult Tests ===

    @Test
    void intersectionResult_validParameters_createsInstance() {
        var result = new IntersectionResult(STREET_NAME, STREET_NAME_2, HELSINKI, COORDS);

        assertEquals(STREET_NAME, result.roadA());
        assertEquals(STREET_NAME_2, result.roadB());
        assertEquals(HELSINKI, result.municipality());
        assertEquals(COORDS, result.coordinates());
    }

    @Test
    void intersectionResult_nullRoadA_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new IntersectionResult(null, STREET_NAME_2, HELSINKI, COORDS));
    }

    @Test
    void intersectionResult_emptyRoadA_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new IntersectionResult(MultilingualName.empty(), STREET_NAME_2, HELSINKI, COORDS));
    }

    @Test
    void intersectionResult_nullRoadB_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new IntersectionResult(STREET_NAME, null, HELSINKI, COORDS));
    }

    @Test
    void intersectionResult_emptyRoadB_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new IntersectionResult(STREET_NAME, MultilingualName.empty(), HELSINKI, COORDS));
    }

    @Test
    void intersectionResult_nullMunicipality_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new IntersectionResult(STREET_NAME, STREET_NAME_2, null, COORDS));
    }

    @Test
    void intersectionResult_nullCoordinates_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new IntersectionResult(STREET_NAME, STREET_NAME_2, HELSINKI, null));
    }

    // === Sealed Interface Tests ===

    @Test
    void locationResult_addressImplementsMunicipalityMethod() {
        LocationResult result = new AddressResult(STREET_NAME, "1", HELSINKI, COORDS, AddressSource.ADDRESS_POINT);
        assertEquals(HELSINKI, result.municipality());
    }

    @Test
    void locationResult_placeImplementsMunicipalityMethod() {
        LocationResult result = new PlaceResult(PLACE_NAME, 48111, HELSINKI, COORDS);
        assertEquals(HELSINKI, result.municipality());
    }

    @Test
    void locationResult_intersectionImplementsMunicipalityMethod() {
        LocationResult result = new IntersectionResult(STREET_NAME, STREET_NAME_2, HELSINKI, COORDS);
        assertEquals(HELSINKI, result.municipality());
    }

    @Test
    void locationResult_addressImplementsCoordinatesMethod() {
        LocationResult result = new AddressResult(STREET_NAME, "1", HELSINKI, COORDS, AddressSource.ADDRESS_POINT);
        assertEquals(COORDS, result.coordinates());
    }

    @Test
    void locationResult_placeImplementsCoordinatesMethod() {
        LocationResult result = new PlaceResult(PLACE_NAME, 48111, HELSINKI, COORDS);
        assertEquals(COORDS, result.coordinates());
    }

    @Test
    void locationResult_intersectionImplementsCoordinatesMethod() {
        LocationResult result = new IntersectionResult(STREET_NAME, STREET_NAME_2, HELSINKI, COORDS);
        assertEquals(COORDS, result.coordinates());
    }

    // === Jackson Serialization Tests ===

    @Test
    void jackson_serializeAddressResult_includesTypeDiscriminator() throws JsonProcessingException {
        var result = new AddressResult(STREET_NAME, "1", HELSINKI, COORDS, AddressSource.ADDRESS_POINT);
        var json = objectMapper.writeValueAsString(result);

        assertTrue(json.contains("\"type\":\"address\""));
        assertTrue(json.contains("\"name\":{"));
        assertTrue(json.contains("\"fi\":\"Mannerheimintie\""));
        assertTrue(json.contains("\"number\":\"1\""));
        assertTrue(json.contains("\"source\":\"address_point\""));
        assertTrue(json.contains("\"municipality\":{"));
        assertTrue(json.contains("\"coordinates\":{"));
        assertTrue(json.contains("\"latitude\":60.169857"));
    }

    @Test
    void jackson_serializePlaceResult_includesTypeDiscriminator() throws JsonProcessingException {
        var result = new PlaceResult(PLACE_NAME, 48111, HELSINKI, COORDS);
        var json = objectMapper.writeValueAsString(result);

        assertTrue(json.contains("\"type\":\"place\""));
        assertTrue(json.contains("\"name\":{"));
        assertTrue(json.contains("\"placeClass\":48111"));
        assertTrue(json.contains("\"municipality\":{"));
        assertTrue(json.contains("\"coordinates\":{"));
    }

    @Test
    void jackson_serializeIntersectionResult_includesTypeDiscriminator() throws JsonProcessingException {
        var result = new IntersectionResult(STREET_NAME, STREET_NAME_2, HELSINKI, COORDS);
        var json = objectMapper.writeValueAsString(result);

        assertTrue(json.contains("\"type\":\"intersection\""));
        assertTrue(json.contains("\"roadA\":{"));
        assertTrue(json.contains("\"roadB\":{"));
        assertTrue(json.contains("\"municipality\":{"));
        assertTrue(json.contains("\"coordinates\":{"));
    }

    @Test
    void jackson_deserializeAddressResult_correctType() throws JsonProcessingException {
        var json = """
                {
                  "type": "address",
                  "name": {"fi": "Mannerheimintie"},
                  "number": "1",
                  "municipality": {"code": "091", "name": {"fi": "Helsinki"}},
                  "coordinates": {"crs": "EPSG:4326", "latitude": 60.169857, "longitude": 24.938379},
                  "source": "address_point"
                }
                """;
        var result = objectMapper.readValue(json, LocationResult.class);

        assertInstanceOf(AddressResult.class, result);
        var address = (AddressResult) result;
        assertEquals("1", address.number());
        assertEquals(AddressSource.ADDRESS_POINT, address.source());
    }

    @Test
    void jackson_deserializePlaceResult_correctType() throws JsonProcessingException {
        var json = """
                {
                  "type": "place",
                  "name": {"fi": "Mannerheiminaukio"},
                  "placeClass": 48111,
                  "municipality": {"code": "091", "name": {"fi": "Helsinki"}},
                  "coordinates": {"crs": "EPSG:4326", "latitude": 60.169857, "longitude": 24.938379}
                }
                """;
        var result = objectMapper.readValue(json, LocationResult.class);

        assertInstanceOf(PlaceResult.class, result);
        var place = (PlaceResult) result;
        assertEquals(48111, place.placeClass());
    }

    @Test
    void jackson_deserializeIntersectionResult_correctType() throws JsonProcessingException {
        var json = """
                {
                  "type": "intersection",
                  "roadA": {"fi": "Mannerheimintie"},
                  "roadB": {"fi": "Kaivokatu"},
                  "municipality": {"code": "091", "name": {"fi": "Helsinki"}},
                  "coordinates": {"crs": "EPSG:4326", "latitude": 60.169857, "longitude": 24.938379}
                }
                """;
        var result = objectMapper.readValue(json, LocationResult.class);

        assertInstanceOf(IntersectionResult.class, result);
        var intersection = (IntersectionResult) result;
        assertEquals("Mannerheimintie", intersection.roadA().get(FINNISH).orElseThrow());
        assertEquals("Kaivokatu", intersection.roadB().get(FINNISH).orElseThrow());
    }

    @Test
    void jackson_roundTripAddressResult_preservesData() throws JsonProcessingException {
        var original = new AddressResult(STREET_NAME, "5A", HELSINKI, COORDS, AddressSource.ROAD_SEGMENT);
        var json = objectMapper.writeValueAsString(original);
        var restored = objectMapper.readValue(json, LocationResult.class);

        assertEquals(original, restored);
    }

    @Test
    void jackson_roundTripPlaceResult_preservesData() throws JsonProcessingException {
        var original = new PlaceResult(PLACE_NAME, 48111, HELSINKI, COORDS);
        var json = objectMapper.writeValueAsString(original);
        var restored = objectMapper.readValue(json, LocationResult.class);

        assertEquals(original, restored);
    }

    @Test
    void jackson_roundTripIntersectionResult_preservesData() throws JsonProcessingException {
        var original = new IntersectionResult(STREET_NAME, STREET_NAME_2, HELSINKI, COORDS);
        var json = objectMapper.writeValueAsString(original);
        var restored = objectMapper.readValue(json, LocationResult.class);

        assertEquals(original, restored);
    }

    @Test
    void jackson_addressSource_serializesAsSnakeCase() throws JsonProcessingException {
        var addressPoint = new AddressResult(STREET_NAME, "1", HELSINKI, COORDS, AddressSource.ADDRESS_POINT);
        var roadSegment = new AddressResult(STREET_NAME, "2", HELSINKI, COORDS, AddressSource.ROAD_SEGMENT);

        var jsonAddressPoint = objectMapper.writeValueAsString(addressPoint);
        var jsonRoadSegment = objectMapper.writeValueAsString(roadSegment);

        assertTrue(jsonAddressPoint.contains("\"source\":\"address_point\""));
        assertTrue(jsonRoadSegment.contains("\"source\":\"road_segment\""));
    }

    // === Equality Tests ===

    @Test
    void addressResult_equals_sameValues_returnsTrue() {
        var r1 = new AddressResult(STREET_NAME, "1", HELSINKI, COORDS, AddressSource.ADDRESS_POINT);
        var r2 = new AddressResult(STREET_NAME, "1", HELSINKI, COORDS, AddressSource.ADDRESS_POINT);
        assertEquals(r1, r2);
    }

    @Test
    void addressResult_equals_differentSource_returnsFalse() {
        var r1 = new AddressResult(STREET_NAME, "1", HELSINKI, COORDS, AddressSource.ADDRESS_POINT);
        var r2 = new AddressResult(STREET_NAME, "1", HELSINKI, COORDS, AddressSource.ROAD_SEGMENT);
        assertNotEquals(r1, r2);
    }

    @Test
    void placeResult_equals_sameValues_returnsTrue() {
        var r1 = new PlaceResult(PLACE_NAME, 48111, HELSINKI, COORDS);
        var r2 = new PlaceResult(PLACE_NAME, 48111, HELSINKI, COORDS);
        assertEquals(r1, r2);
    }

    @Test
    void intersectionResult_equals_sameValues_returnsTrue() {
        var r1 = new IntersectionResult(STREET_NAME, STREET_NAME_2, HELSINKI, COORDS);
        var r2 = new IntersectionResult(STREET_NAME, STREET_NAME_2, HELSINKI, COORDS);
        assertEquals(r1, r2);
    }
}

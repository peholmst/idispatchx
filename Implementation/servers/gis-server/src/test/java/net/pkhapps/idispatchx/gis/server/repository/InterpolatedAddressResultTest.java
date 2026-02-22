package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.Language;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterpolatedAddressResultTest {

    private static final MultilingualName STREET_NAME = MultilingualName.of(
            Language.of("fi"), "Mannerheimintie");
    private static final Coordinates.Epsg4326 COORDINATES = Coordinates.Epsg4326.of(60.1699, 24.9384);
    private static final Municipality MUNICIPALITY = Municipality.of(
            MunicipalityCode.of("091"),
            MultilingualName.of(Language.of("fi"), "Helsinki"));

    @Test
    void constructor_withValidValues_createsResult() {
        var result = new InterpolatedAddressResult(
                STREET_NAME, "12", MUNICIPALITY, COORDINATES);

        assertEquals(STREET_NAME, result.streetName());
        assertEquals("12", result.number());
        assertEquals(MUNICIPALITY, result.municipality());
        assertEquals(COORDINATES, result.coordinates());
    }

    @Test
    void constructor_withNullMunicipality_createsResult() {
        var result = new InterpolatedAddressResult(
                STREET_NAME, "12", null, COORDINATES);

        assertNull(result.municipality());
    }

    @Test
    void constructor_withNullStreetName_throws() {
        assertThrows(NullPointerException.class, () ->
                new InterpolatedAddressResult(null, "12", MUNICIPALITY, COORDINATES));
    }

    @Test
    void constructor_withNullNumber_throws() {
        assertThrows(NullPointerException.class, () ->
                new InterpolatedAddressResult(STREET_NAME, null, MUNICIPALITY, COORDINATES));
    }

    @Test
    void constructor_withNullCoordinates_throws() {
        assertThrows(NullPointerException.class, () ->
                new InterpolatedAddressResult(STREET_NAME, "12", MUNICIPALITY, null));
    }

    @Test
    void constructor_withAlphanumericNumber_createsResult() {
        var result = new InterpolatedAddressResult(
                STREET_NAME, "12A", MUNICIPALITY, COORDINATES);

        assertEquals("12A", result.number());
    }

    @Test
    void constructor_withEmptyNumber_createsResult() {
        // Empty number is allowed (some addresses may not have a number)
        var result = new InterpolatedAddressResult(
                STREET_NAME, "", MUNICIPALITY, COORDINATES);

        assertEquals("", result.number());
    }
}

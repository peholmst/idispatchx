package net.pkhapps.idispatchx.gis.server.service.geocode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParsedQueryTest {

    // ==================== AddressQuery ====================

    @Test
    void addressQuery_validInput_createsInstance() {
        var query = new ParsedQuery.AddressQuery("Mannerheimintie", 5);
        assertEquals("Mannerheimintie", query.streetName());
        assertEquals(5, query.number());
    }

    @Test
    void addressQuery_nullStreetName_throwsNpe() {
        assertThrows(NullPointerException.class, () -> new ParsedQuery.AddressQuery(null, 5));
    }

    @Test
    void addressQuery_blankStreetName_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new ParsedQuery.AddressQuery("  ", 5));
    }

    @Test
    void addressQuery_zeroNumber_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new ParsedQuery.AddressQuery("Mannerheimintie", 0));
    }

    @Test
    void addressQuery_negativeNumber_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new ParsedQuery.AddressQuery("Mannerheimintie", -1));
    }

    // ==================== StreetQuery ====================

    @Test
    void streetQuery_validInput_createsInstance() {
        var query = new ParsedQuery.StreetQuery("Mannerheimintie");
        assertEquals("Mannerheimintie", query.name());
    }

    @Test
    void streetQuery_nullName_throwsNpe() {
        assertThrows(NullPointerException.class, () -> new ParsedQuery.StreetQuery(null));
    }

    @Test
    void streetQuery_blankName_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new ParsedQuery.StreetQuery("  "));
    }

    // ==================== IntersectionQuery ====================

    @Test
    void intersectionQuery_validInput_createsInstance() {
        var query = new ParsedQuery.IntersectionQuery("Mannerheimintie", "Aleksanterinkatu");
        assertEquals("Mannerheimintie", query.roadA());
        assertEquals("Aleksanterinkatu", query.roadB());
    }

    @Test
    void intersectionQuery_nullRoadA_throwsNpe() {
        assertThrows(NullPointerException.class, () -> new ParsedQuery.IntersectionQuery(null, "Aleksanterinkatu"));
    }

    @Test
    void intersectionQuery_nullRoadB_throwsNpe() {
        assertThrows(NullPointerException.class, () -> new ParsedQuery.IntersectionQuery("Mannerheimintie", null));
    }

    @Test
    void intersectionQuery_blankRoadA_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new ParsedQuery.IntersectionQuery("  ", "Aleksanterinkatu"));
    }

    @Test
    void intersectionQuery_blankRoadB_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new ParsedQuery.IntersectionQuery("Mannerheimintie", "  "));
    }

    // ==================== PlaceQuery ====================

    @Test
    void placeQuery_validInput_createsInstance() {
        var query = new ParsedQuery.PlaceQuery("Helsinki");
        assertEquals("Helsinki", query.name());
    }

    @Test
    void placeQuery_nullName_throwsNpe() {
        assertThrows(NullPointerException.class, () -> new ParsedQuery.PlaceQuery(null));
    }

    @Test
    void placeQuery_blankName_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new ParsedQuery.PlaceQuery("  "));
    }
}

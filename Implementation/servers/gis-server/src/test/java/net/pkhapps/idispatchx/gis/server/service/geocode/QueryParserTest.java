package net.pkhapps.idispatchx.gis.server.service.geocode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueryParserTest {

    private final QueryParser parser = new QueryParser();

    // ==================== Intersection detection ====================

    @Test
    void parse_intersectionWithSlash_returnsIntersectionQuery() {
        var result = parser.parse("Mannerheimintie / Aleksanterinkatu");
        assertInstanceOf(ParsedQuery.IntersectionQuery.class, result);
        var iq = (ParsedQuery.IntersectionQuery) result;
        assertEquals("Mannerheimintie", iq.roadA());
        assertEquals("Aleksanterinkatu", iq.roadB());
    }

    @Test
    void parse_intersectionWithAmpersand_returnsIntersectionQuery() {
        var result = parser.parse("Mannerheimintie & Aleksanterinkatu");
        assertInstanceOf(ParsedQuery.IntersectionQuery.class, result);
    }

    @Test
    void parse_intersectionWithAnd_returnsIntersectionQuery() {
        var result = parser.parse("Mannerheimintie and Aleksanterinkatu");
        assertInstanceOf(ParsedQuery.IntersectionQuery.class, result);
    }

    @Test
    void parse_intersectionWithJa_returnsIntersectionQuery() {
        var result = parser.parse("Mannerheimintie ja Aleksanterinkatu");
        assertInstanceOf(ParsedQuery.IntersectionQuery.class, result);
    }

    @Test
    void parse_intersectionWithOch_returnsIntersectionQuery() {
        var result = parser.parse("Mannerheimintie och Aleksanterinkatu");
        assertInstanceOf(ParsedQuery.IntersectionQuery.class, result);
    }

    @Test
    void parse_intersectionCaseInsensitiveSeparator_returnsIntersectionQuery() {
        var result = parser.parse("Mannerheimintie JA Aleksanterinkatu");
        assertInstanceOf(ParsedQuery.IntersectionQuery.class, result);
    }

    @Test
    void parse_intersectionWithoutLetters_fallsThrough() {
        // "123 / 456" — no letters on either side
        var result = parser.parse("123 / 456");
        // Should not be intersection since neither part contains letters
        assertNotEquals(ParsedQuery.IntersectionQuery.class, result.getClass());
    }

    // ==================== Address detection ====================

    @Test
    void parse_addressWithNumber_returnsAddressQuery() {
        var result = parser.parse("Mannerheimintie 5");
        assertInstanceOf(ParsedQuery.AddressQuery.class, result);
        var aq = (ParsedQuery.AddressQuery) result;
        assertEquals("Mannerheimintie", aq.streetName());
        assertEquals(5, aq.number());
    }

    @Test
    void parse_addressWithLargeNumber_returnsAddressQuery() {
        var result = parser.parse("Iso Roobertinkatu 123");
        assertInstanceOf(ParsedQuery.AddressQuery.class, result);
        var aq = (ParsedQuery.AddressQuery) result;
        assertEquals("Iso Roobertinkatu", aq.streetName());
        assertEquals(123, aq.number());
    }

    @Test
    void parse_addressWithTrailingSpace_returnsAddressQuery() {
        var result = parser.parse("Mannerheimintie 5 ");
        assertInstanceOf(ParsedQuery.AddressQuery.class, result);
    }

    // ==================== Street detection ====================

    @Test
    void parse_multipleWordsWithoutNumber_returnsStreetQuery() {
        var result = parser.parse("Iso Roobertinkatu");
        assertInstanceOf(ParsedQuery.StreetQuery.class, result);
        assertEquals("Iso Roobertinkatu", ((ParsedQuery.StreetQuery) result).name());
    }

    // ==================== Place detection ====================

    @Test
    void parse_singleWord_returnsPlaceQuery() {
        var result = parser.parse("Helsinki");
        assertInstanceOf(ParsedQuery.PlaceQuery.class, result);
        assertEquals("Helsinki", ((ParsedQuery.PlaceQuery) result).name());
    }

    @Test
    void parse_singleWordWithSurroundingSpaces_returnsPlaceQuery() {
        var result = parser.parse("  Helsinki  ");
        assertInstanceOf(ParsedQuery.PlaceQuery.class, result);
        assertEquals("Helsinki", ((ParsedQuery.PlaceQuery) result).name());
    }

    // ==================== Error cases ====================

    @Test
    void parse_nullQuery_throwsNpe() {
        assertThrows(NullPointerException.class, () -> parser.parse(null));
    }

    @Test
    void parse_blankQuery_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("   "));
    }

    // ==================== Edge cases ====================

    @Test
    void parse_numberOnly_returnsPlaceQuery() {
        // "123" has no whitespace, so it falls through to PlaceQuery
        var result = parser.parse("123");
        assertInstanceOf(ParsedQuery.PlaceQuery.class, result);
    }

    @Test
    void parse_intersectionWithThreeParts_fallsThrough() {
        // "A / B / C" splits into 3 parts, not 2
        var result = parser.parse("Road A / Road B / Road C");
        assertNotEquals(ParsedQuery.IntersectionQuery.class, result.getClass());
    }
}

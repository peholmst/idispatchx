package net.pkhapps.idispatchx.cad.domain.model.shared.location;

import net.pkhapps.idispatchx.common.domain.model.Language;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocationTest {

    private static final Municipality MUNICIPALITY =
            Municipality.withoutCode(MultilingualName.withUnspecifiedLanguage("Espoo"));
    private static final MultilingualName NAME =
            MultilingualName.withUnspecifiedLanguage("Mannerheimintie");
    private static final MultilingualName NAME_B =
            MultilingualName.withUnspecifiedLanguage("Tehtaankatu");

    @Test
    void exactAddress_requiredFields() {
        var loc = new Location.ExactAddress(MUNICIPALITY, NAME, null, null, null);
        assertEquals(MUNICIPALITY, loc.municipality());
        assertEquals(NAME, loc.addressName());
        assertNull(loc.addressNumber());
        assertNull(loc.coordinates());
        assertNull(loc.additionalDetails());
    }

    @Test
    void exactAddress_rejectsNullMunicipality() {
        assertThrows(NullPointerException.class,
                () -> new Location.ExactAddress(null, NAME, null, null, null));
    }

    @Test
    void exactAddress_rejectsNullAddressName() {
        assertThrows(NullPointerException.class,
                () -> new Location.ExactAddress(MUNICIPALITY, null, null, null, null));
    }

    @Test
    void exactAddress_rejectsAddressNumberExceeding30Chars() {
        var tooLong = "A".repeat(31);
        assertThrows(IllegalArgumentException.class,
                () -> new Location.ExactAddress(MUNICIPALITY, NAME, tooLong, null, null));
    }

    @Test
    void exactAddress_accepts30CharAddressNumber() {
        var ok = "A".repeat(30);
        assertDoesNotThrow(() -> new Location.ExactAddress(MUNICIPALITY, NAME, ok, null, null));
    }

    @Test
    void exactAddress_rejectsAdditionalDetailsExceeding1000Chars() {
        var tooLong = "A".repeat(1001);
        assertThrows(IllegalArgumentException.class,
                () -> new Location.ExactAddress(MUNICIPALITY, NAME, null, null, tooLong));
    }

    @Test
    void roadIntersection_requiredFields() {
        var loc = new Location.RoadIntersection(MUNICIPALITY, NAME, NAME_B, null, null);
        assertEquals(MUNICIPALITY, loc.municipality());
        assertEquals(NAME, loc.roadNameA());
        assertEquals(NAME_B, loc.roadNameB());
    }

    @Test
    void roadIntersection_rejectsNullFields() {
        assertThrows(NullPointerException.class,
                () -> new Location.RoadIntersection(null, NAME, NAME_B, null, null));
        assertThrows(NullPointerException.class,
                () -> new Location.RoadIntersection(MUNICIPALITY, null, NAME_B, null, null));
        assertThrows(NullPointerException.class,
                () -> new Location.RoadIntersection(MUNICIPALITY, NAME, null, null, null));
    }

    @Test
    void namedPlace_requiredFields() {
        var loc = new Location.NamedPlace(MUNICIPALITY, NAME, null, null);
        assertEquals(MUNICIPALITY, loc.municipality());
        assertEquals(NAME, loc.name());
    }

    @Test
    void namedPlace_rejectsNullFields() {
        assertThrows(NullPointerException.class,
                () -> new Location.NamedPlace(null, NAME, null, null));
        assertThrows(NullPointerException.class,
                () -> new Location.NamedPlace(MUNICIPALITY, null, null, null));
    }

    @Test
    void relativeLocation_requiresAdditionalDetails() {
        assertThrows(NullPointerException.class,
                () -> new Location.RelativeLocation(MUNICIPALITY, NAME, null, null));
    }

    @Test
    void relativeLocation_rejectsAdditionalDetailsExceeding1000Chars() {
        var tooLong = "A".repeat(1001);
        assertThrows(IllegalArgumentException.class,
                () -> new Location.RelativeLocation(MUNICIPALITY, NAME, tooLong, null));
    }

    @Test
    void relativeLocation_accepts1000CharAdditionalDetails() {
        var ok = "A".repeat(1000);
        assertDoesNotThrow(() -> new Location.RelativeLocation(MUNICIPALITY, NAME, ok, null));
    }
}

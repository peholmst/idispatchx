package net.pkhapps.idispatchx.gis.importer.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeatureTypeTest {

    @Test
    void elementNames_matchGmlElementNames() {
        assertEquals("Kunta", FeatureType.KUNTA.elementName());
        assertEquals("Tieviiva", FeatureType.TIEVIIVA.elementName());
        assertEquals("Osoitepiste", FeatureType.OSOITEPISTE.elementName());
        assertEquals("Paikannimi", FeatureType.PAIKANNIMI.elementName());
    }

    @Test
    void values_containsAllFourTypes() {
        assertEquals(4, FeatureType.values().length);
    }
}

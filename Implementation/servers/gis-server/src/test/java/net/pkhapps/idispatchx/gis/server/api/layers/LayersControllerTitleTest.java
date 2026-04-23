package net.pkhapps.idispatchx.gis.server.api.layers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayersControllerTitleTest {

    @Test
    void toTitle_plainWord_returnsCapitalized() {
        assertEquals("Terrain", LayersController.toTitle("terrain"));
    }

    @Test
    void toTitle_hyphenatedWord_returnsCapitalizedWords() {
        assertEquals("Road Network", LayersController.toTitle("road-network"));
    }

    @Test
    void toTitle_underscoredWord_returnsCapitalizedWords() {
        assertEquals("Road Network", LayersController.toTitle("road_network"));
    }

    @Test
    void toTitle_mixedSeparators_returnsCapitalizedWords() {
        assertEquals("Road Network Map", LayersController.toTitle("road-network_map"));
    }

    @Test
    void toTitle_alreadyUpperCase_lowercasesRest() {
        assertEquals("Terrain", LayersController.toTitle("TERRAIN"));
    }

    @Test
    void toTitle_navigation_returnsNavigation() {
        assertEquals("Navigation", LayersController.toTitle("navigation"));
    }
}

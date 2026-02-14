package net.pkhapps.idispatchx.gis.importer.parser;

/**
 * The four NLS topographic feature types relevant to geocoding.
 */
public enum FeatureType {

    KUNTA("Kunta"),
    TIEVIIVA("Tieviiva"),
    OSOITEPISTE("Osoitepiste"),
    PAIKANNIMI("Paikannimi");

    private final String elementName;

    FeatureType(String elementName) {
        this.elementName = elementName;
    }

    /**
     * Returns the GML element local name for this feature type.
     */
    public String elementName() {
        return elementName;
    }
}

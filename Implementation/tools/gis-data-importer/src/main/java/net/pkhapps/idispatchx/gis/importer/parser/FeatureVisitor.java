package net.pkhapps.idispatchx.gis.importer.parser;

import net.pkhapps.idispatchx.gis.importer.parser.model.KuntaFeature;
import net.pkhapps.idispatchx.gis.importer.parser.model.OsoitepisteFeature;
import net.pkhapps.idispatchx.gis.importer.parser.model.PaikannimiFeature;
import net.pkhapps.idispatchx.gis.importer.parser.model.TieviivaFeature;

/**
 * Callback interface for receiving parsed GML features from {@link NlsGmlParser}.
 */
public interface FeatureVisitor {

    /**
     * Called when a Kunta (municipality boundary) feature has been parsed.
     */
    void onKunta(KuntaFeature feature);

    /**
     * Called when a Tieviiva (road segment) feature has been parsed.
     */
    void onTieviiva(TieviivaFeature feature);

    /**
     * Called when an Osoitepiste (address point) feature has been parsed.
     */
    void onOsoitepiste(OsoitepisteFeature feature);

    /**
     * Called when a Paikannimi (place name) feature has been parsed.
     */
    void onPaikannimi(PaikannimiFeature feature);
}

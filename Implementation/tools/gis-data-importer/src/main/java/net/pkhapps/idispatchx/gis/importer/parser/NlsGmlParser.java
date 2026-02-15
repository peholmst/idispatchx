package net.pkhapps.idispatchx.gis.importer.parser;

import net.pkhapps.idispatchx.gis.importer.parser.model.KuntaFeature;
import net.pkhapps.idispatchx.gis.importer.parser.model.OsoitepisteFeature;
import net.pkhapps.idispatchx.gis.importer.parser.model.PaikannimiFeature;
import net.pkhapps.idispatchx.gis.importer.parser.model.TieviivaFeature;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * StAX-based parser for NLS (National Land Survey of Finland) topographic GML files.
 * Extracts four feature types relevant to geocoding: Kunta, Tieviiva, Osoitepiste, and Paikannimi.
 * <p>
 * Parsed features are delivered to a {@link FeatureVisitor} callback. Features that cannot be parsed
 * (e.g., missing required fields) are logged as warnings and skipped.
 */
public final class NlsGmlParser {

    private static final Logger log = LoggerFactory.getLogger(NlsGmlParser.class);

    private NlsGmlParser() {
    }

    /**
     * Parses all four feature types from the given GML input stream.
     *
     * @param input   the GML input stream
     * @param visitor callback for parsed features
     * @throws XMLStreamException if the XML is fundamentally malformed
     */
    public static void parse(InputStream input, FeatureVisitor visitor) throws XMLStreamException {
        parse(input, visitor, EnumSet.allOf(FeatureType.class));
    }

    /**
     * Parses the specified feature types from the given GML input stream.
     *
     * @param input        the GML input stream
     * @param visitor      callback for parsed features
     * @param featureTypes the feature types to parse; others are skipped
     * @throws XMLStreamException if the XML is fundamentally malformed
     */
    public static void parse(InputStream input, FeatureVisitor visitor, Set<FeatureType> featureTypes)
            throws XMLStreamException {
        var factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        var reader = factory.createXMLStreamReader(input);
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    var localName = reader.getLocalName();
                    if (featureTypes.contains(FeatureType.KUNTA) && "Kunta".equals(localName)) {
                        tryParseKunta(reader, visitor);
                    } else if (featureTypes.contains(FeatureType.TIEVIIVA) && "Tieviiva".equals(localName)) {
                        tryParseTieviiva(reader, visitor);
                    } else if (featureTypes.contains(FeatureType.OSOITEPISTE) && "Osoitepiste".equals(localName)) {
                        tryParseOsoitepiste(reader, visitor);
                    } else if (featureTypes.contains(FeatureType.PAIKANNIMI) && "Paikannimi".equals(localName)) {
                        tryParsePaikannimi(reader, visitor);
                    }
                }
            }
        } finally {
            reader.close();
        }
    }

    private static void tryParseKunta(XMLStreamReader reader, FeatureVisitor visitor) throws XMLStreamException {
        KuntaFeature feature;
        try {
            feature = parseKunta(reader);
        } catch (XMLStreamException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse Kunta feature, skipping: {}", e.getMessage());
            return;
        }
        visitor.onKunta(feature);
    }

    private static void tryParseTieviiva(XMLStreamReader reader, FeatureVisitor visitor) throws XMLStreamException {
        TieviivaFeature feature;
        try {
            feature = parseTieviiva(reader);
        } catch (XMLStreamException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse Tieviiva feature, skipping: {}", e.getMessage());
            return;
        }
        visitor.onTieviiva(feature);
    }

    private static void tryParseOsoitepiste(XMLStreamReader reader, FeatureVisitor visitor) throws XMLStreamException {
        OsoitepisteFeature feature;
        try {
            feature = parseOsoitepiste(reader);
        } catch (XMLStreamException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse Osoitepiste feature, skipping: {}", e.getMessage());
            return;
        }
        visitor.onOsoitepiste(feature);
    }

    private static void tryParsePaikannimi(XMLStreamReader reader, FeatureVisitor visitor) throws XMLStreamException {
        PaikannimiFeature feature;
        try {
            feature = parsePaikannimi(reader);
        } catch (XMLStreamException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse Paikannimi feature, skipping: {}", e.getMessage());
            return;
        }
        visitor.onPaikannimi(feature);
    }

    private static KuntaFeature parseKunta(XMLStreamReader reader) throws XMLStreamException {
        long gid = Long.parseLong(reader.getAttributeValue(null, "gid"));
        LocalDate alkupvm = null;
        @Nullable LocalDate loppupvm = null;
        String kuntatunnus = null;
        double[][] polygonCoordinates = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "alkupvm" -> alkupvm = LocalDate.parse(readElementText(reader));
                    case "loppupvm" -> loppupvm = LocalDate.parse(readElementText(reader));
                    case "kuntatunnus" -> kuntatunnus = readElementText(reader);
                    case "Alue" -> polygonCoordinates = parsePosList(reader, "Alue");
                    default -> { /* skip */ }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "Kunta".equals(reader.getLocalName())) {
                break;
            }
        }

        if (alkupvm == null) throw new IllegalStateException("Missing alkupvm");
        if (kuntatunnus == null) throw new IllegalStateException("Missing kuntatunnus");
        if (polygonCoordinates == null) throw new IllegalStateException("Missing polygon coordinates");

        return new KuntaFeature(gid, alkupvm, loppupvm, kuntatunnus, polygonCoordinates);
    }

    private static TieviivaFeature parseTieviiva(XMLStreamReader reader) throws XMLStreamException {
        long gid = Long.parseLong(reader.getAttributeValue(null, "gid"));
        LocalDate alkupvm = null;
        @Nullable LocalDate loppupvm = null;
        int kohdeluokka = 0;
        boolean kohdeluokkaSet = false;
        int paallyste = 0;
        boolean paallystemSet = false;
        @Nullable Integer hallinnollinenLuokka = null;
        int yksisuuntaisuus = 0;
        boolean yksisuuntaisuusSet = false;
        @Nullable String nameFi = null;
        @Nullable String nameSv = null;
        @Nullable String nameSmn = null;
        @Nullable String nameSms = null;
        @Nullable String nameSme = null;
        @Nullable Integer minAddressLeft = null;
        @Nullable Integer maxAddressLeft = null;
        @Nullable Integer minAddressRight = null;
        @Nullable Integer maxAddressRight = null;
        @Nullable String kuntatunnus = null;
        double[][] lineCoordinates = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "alkupvm" -> alkupvm = LocalDate.parse(readElementText(reader));
                    case "loppupvm" -> loppupvm = LocalDate.parse(readElementText(reader));
                    case "kohdeluokka" -> { kohdeluokka = Integer.parseInt(readElementText(reader)); kohdeluokkaSet = true; }
                    case "paallyste" -> { paallyste = Integer.parseInt(readElementText(reader)); paallystemSet = true; }
                    case "hallinnollinenLuokka" -> hallinnollinenLuokka = Integer.parseInt(readElementText(reader));
                    case "yksisuuntaisuus" -> { yksisuuntaisuus = Integer.parseInt(readElementText(reader)); yksisuuntaisuusSet = true; }
                    case "nimi_suomi" -> nameFi = readElementText(reader);
                    case "nimi_ruotsi" -> nameSv = readElementText(reader);
                    case "nimi_inarinsaame" -> nameSmn = readElementText(reader);
                    case "nimi_koltansaame" -> nameSms = readElementText(reader);
                    case "nimi_pohjoissaame" -> nameSme = readElementText(reader);
                    case "minOsoitenumeroVasen" -> minAddressLeft = zeroToNull(Integer.parseInt(readElementText(reader)));
                    case "maxOsoitenumeroVasen" -> maxAddressLeft = zeroToNull(Integer.parseInt(readElementText(reader)));
                    case "minOsoitenumeroOikea" -> minAddressRight = zeroToNull(Integer.parseInt(readElementText(reader)));
                    case "maxOsoitenumeroOikea" -> maxAddressRight = zeroToNull(Integer.parseInt(readElementText(reader)));
                    case "kuntatunnus" -> kuntatunnus = readElementText(reader);
                    case "Murtoviiva" -> lineCoordinates = parsePosList(reader, "Murtoviiva");
                    default -> { /* skip */ }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "Tieviiva".equals(reader.getLocalName())) {
                break;
            }
        }

        if (alkupvm == null) throw new IllegalStateException("Missing alkupvm");
        if (!kohdeluokkaSet) throw new IllegalStateException("Missing kohdeluokka");
        if (!paallystemSet) throw new IllegalStateException("Missing paallyste");
        if (!yksisuuntaisuusSet) throw new IllegalStateException("Missing yksisuuntaisuus");
        if (lineCoordinates == null) throw new IllegalStateException("Missing line coordinates");

        return new TieviivaFeature(gid, alkupvm, loppupvm, kohdeluokka, paallyste, hallinnollinenLuokka,
                yksisuuntaisuus, nameFi, nameSv, nameSmn, nameSms, nameSme,
                minAddressLeft, maxAddressLeft, minAddressRight, maxAddressRight, kuntatunnus, lineCoordinates);
    }

    private static OsoitepisteFeature parseOsoitepiste(XMLStreamReader reader) throws XMLStreamException {
        long gid = Long.parseLong(reader.getAttributeValue(null, "gid"));
        LocalDate alkupvm = null;
        @Nullable LocalDate loppupvm = null;
        @Nullable String numero = null;
        @Nullable String nameFi = null;
        @Nullable String nameSv = null;
        @Nullable String nameSmn = null;
        @Nullable String nameSms = null;
        @Nullable String nameSme = null;
        @Nullable String kuntatunnus = null;
        double[] point = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "alkupvm" -> alkupvm = LocalDate.parse(readElementText(reader));
                    case "loppupvm" -> loppupvm = LocalDate.parse(readElementText(reader));
                    case "numero" -> numero = readElementText(reader);
                    case "nimi_suomi" -> nameFi = readElementText(reader);
                    case "nimi_ruotsi" -> nameSv = readElementText(reader);
                    case "nimi_inarinsaame" -> nameSmn = readElementText(reader);
                    case "nimi_koltansaame" -> nameSms = readElementText(reader);
                    case "nimi_pohjoissaame" -> nameSme = readElementText(reader);
                    case "kuntatunnus" -> kuntatunnus = readElementText(reader);
                    case "Piste" -> point = parsePoint(reader);
                    default -> { /* skip */ }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "Osoitepiste".equals(reader.getLocalName())) {
                break;
            }
        }

        if (alkupvm == null) throw new IllegalStateException("Missing alkupvm");
        if (point == null) throw new IllegalStateException("Missing point coordinates");

        return new OsoitepisteFeature(gid, alkupvm, loppupvm, numero, nameFi, nameSv, nameSmn, nameSms, nameSme,
                kuntatunnus, point[0], point[1]);
    }

    private static PaikannimiFeature parsePaikannimi(XMLStreamReader reader) throws XMLStreamException {
        long gid = Long.parseLong(reader.getAttributeValue(null, "gid"));
        LocalDate alkupvm = null;
        @Nullable LocalDate loppupvm = null;
        String teksti = null;
        String kieli = null;
        int kohdeluokka = 0;
        boolean kohdeluokkaSet = false;
        @Nullable Long karttanimiId = null;
        double[] point = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "alkupvm" -> alkupvm = LocalDate.parse(readElementText(reader));
                    case "loppupvm" -> loppupvm = LocalDate.parse(readElementText(reader));
                    case "teksti" -> {
                        kieli = reader.getAttributeValue(null, "kieli");
                        teksti = readElementText(reader);
                    }
                    case "kohdeluokka" -> { kohdeluokka = Integer.parseInt(readElementText(reader)); kohdeluokkaSet = true; }
                    case "nrKarttanimiId" -> karttanimiId = Long.parseLong(readElementText(reader));
                    case "Piste" -> point = parsePoint(reader);
                    default -> { /* skip */ }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "Paikannimi".equals(reader.getLocalName())) {
                break;
            }
        }

        if (alkupvm == null) throw new IllegalStateException("Missing alkupvm");
        if (teksti == null) throw new IllegalStateException("Missing teksti");
        if (kieli == null) throw new IllegalStateException("Missing kieli attribute on teksti");
        if (!kohdeluokkaSet) throw new IllegalStateException("Missing kohdeluokka");
        if (point == null) throw new IllegalStateException("Missing point coordinates");

        return new PaikannimiFeature(gid, alkupvm, loppupvm, teksti, kieli, kohdeluokka, karttanimiId,
                point[0], point[1]);
    }

    /**
     * Parses a {@code <Piste><gml:pos>} element and returns {@code [easting, northing]}.
     * Elevation (if present) is dropped.
     */
    private static double[] parsePoint(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && "pos".equals(reader.getLocalName())) {
                var text = readElementText(reader);
                var parts = text.trim().split("\\s+");
                return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
            } else if (event == XMLStreamConstants.END_ELEMENT && "Piste".equals(reader.getLocalName())) {
                break;
            }
        }
        throw new IllegalStateException("No gml:pos found inside Piste");
    }

    /**
     * Parses a {@code <gml:posList>} element within the given enclosing element and returns coordinate pairs.
     * Uses the {@code srsDimension} attribute to determine stride (2 or 3). Elevation is dropped.
     * Stops scanning when the end of {@code enclosingElement} is reached.
     */
    private static double[][] parsePosList(XMLStreamReader reader, String enclosingElement) throws XMLStreamException {
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && "posList".equals(reader.getLocalName())) {
                int srsDimension = 2;
                var dimAttr = reader.getAttributeValue(null, "srsDimension");
                if (dimAttr != null) {
                    srsDimension = Integer.parseInt(dimAttr);
                }
                var text = readElementText(reader);
                var parts = text.trim().split("\\s+");
                if (parts.length % srsDimension != 0) {
                    throw new IllegalStateException(
                            "posList value count " + parts.length + " is not divisible by srsDimension " + srsDimension);
                }
                int numCoords = parts.length / srsDimension;
                var coordinates = new double[numCoords][2];
                for (int i = 0; i < numCoords; i++) {
                    int offset = i * srsDimension;
                    coordinates[i][0] = Double.parseDouble(parts[offset]);
                    coordinates[i][1] = Double.parseDouble(parts[offset + 1]);
                }
                return coordinates;
            } else if (event == XMLStreamConstants.END_ELEMENT && enclosingElement.equals(reader.getLocalName())) {
                break;
            }
        }
        throw new IllegalStateException("No gml:posList found inside " + enclosingElement);
    }

    /**
     * Reads the text content of the current element and returns it.
     * Consumes events until the element's END_ELEMENT is reached.
     */
    private static String readElementText(XMLStreamReader reader) throws XMLStreamException {
        return reader.getElementText();
    }

    /**
     * Returns {@code null} if the value is 0, otherwise returns the value.
     * Used for address range fields where 0 means "no addresses on this side".
     */
    private static @Nullable Integer zeroToNull(int value) {
        return value == 0 ? null : value;
    }
}

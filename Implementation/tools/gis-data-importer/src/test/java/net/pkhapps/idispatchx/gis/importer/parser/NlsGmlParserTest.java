package net.pkhapps.idispatchx.gis.importer.parser;

import net.pkhapps.idispatchx.gis.importer.parser.model.KuntaFeature;
import net.pkhapps.idispatchx.gis.importer.parser.model.OsoitepisteFeature;
import net.pkhapps.idispatchx.gis.importer.parser.model.PaikannimiFeature;
import net.pkhapps.idispatchx.gis.importer.parser.model.TieviivaFeature;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NlsGmlParserTest {

    // === Helper classes ===

    private static class CollectingVisitor implements FeatureVisitor {
        final List<KuntaFeature> kunnat = new ArrayList<>();
        final List<TieviivaFeature> tieviivat = new ArrayList<>();
        final List<OsoitepisteFeature> osoitepisteet = new ArrayList<>();
        final List<PaikannimiFeature> paikannimet = new ArrayList<>();

        @Override
        public void onKunta(KuntaFeature feature) {
            kunnat.add(feature);
        }

        @Override
        public void onTieviiva(TieviivaFeature feature) {
            tieviivat.add(feature);
        }

        @Override
        public void onOsoitepiste(OsoitepisteFeature feature) {
            osoitepisteet.add(feature);
        }

        @Override
        public void onPaikannimi(PaikannimiFeature feature) {
            paikannimet.add(feature);
        }
    }

    private static InputStream gmlStream(String featureXml) {
        var doc = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Maastotiedot xmlns="http://xml.nls.fi/XML/Namespace/Maastotietojarjestelma/SiirtotiedostonMalli/2011-02"
                              xmlns:gml="http://www.opengis.net/gml"
                              srsName="EPSG:3067, EPSG:5717">
                """ + featureXml + """
                </Maastotiedot>
                """;
        return new ByteArrayInputStream(doc.getBytes(StandardCharsets.UTF_8));
    }

    // === Kunta parsing ===

    @Test
    void parseKunta_fullFields_parsesCorrectly() throws XMLStreamException {
        var xml = """
                <kunnat>
                    <Kunta gid="27696440" dimension="3">
                        <sijaintitarkkuus>0</sijaintitarkkuus>
                        <aineistolahde>1</aineistolahde>
                        <alkupvm>2009-01-01</alkupvm>
                        <sijainti>
                            <Piste><gml:pos srsDimension="3">230000.000 6672000.001 0.000</gml:pos></Piste>
                            <Alue>
                                <gml:exterior>
                                    <gml:LinearRing>
                                        <gml:posList srsDimension="3">234265.396 6669060.097 0.000 231796.382 6666000.000 0.000 232000.000 6666000.000 0.000 234265.396 6669060.097 0.000</gml:posList>
                                    </gml:LinearRing>
                                </gml:exterior>
                            </Alue>
                        </sijainti>
                        <kohderyhma>71</kohderyhma>
                        <kohdeluokka>84200</kohdeluokka>
                        <kuntatunnus>445</kuntatunnus>
                    </Kunta>
                </kunnat>
                """;
        var visitor = new CollectingVisitor();
        NlsGmlParser.parse(gmlStream(xml), visitor);

        assertEquals(1, visitor.kunnat.size());
        var kunta = visitor.kunnat.getFirst();
        assertEquals(27696440L, kunta.gid());
        assertEquals(LocalDate.of(2009, 1, 1), kunta.alkupvm());
        assertNull(kunta.loppupvm());
        assertEquals("445", kunta.kuntatunnus());
        assertEquals(4, kunta.polygonCoordinates().length);
        assertEquals(234265.396, kunta.polygonCoordinates()[0][0], 1e-3);
        assertEquals(6669060.097, kunta.polygonCoordinates()[0][1], 1e-3);
    }

    @Test
    void parseKunta_withLoppupvm_parsesEndDate() throws XMLStreamException {
        var xml = """
                <kunnat>
                    <Kunta gid="12345" dimension="2">
                        <alkupvm>2009-01-01</alkupvm>
                        <loppupvm>2020-06-15</loppupvm>
                        <sijainti>
                            <Alue>
                                <gml:exterior>
                                    <gml:LinearRing>
                                        <gml:posList srsDimension="2">100.0 200.0 300.0 400.0 100.0 200.0</gml:posList>
                                    </gml:LinearRing>
                                </gml:exterior>
                            </Alue>
                        </sijainti>
                        <kuntatunnus>100</kuntatunnus>
                    </Kunta>
                </kunnat>
                """;
        var visitor = new CollectingVisitor();
        NlsGmlParser.parse(gmlStream(xml), visitor);

        assertEquals(1, visitor.kunnat.size());
        assertEquals(LocalDate.of(2020, 6, 15), visitor.kunnat.getFirst().loppupvm());
    }

    @Test
    void parseKunta_2dCoordinates_parsesCorrectly() throws XMLStreamException {
        var xml = """
                <kunnat>
                    <Kunta gid="1" dimension="2">
                        <alkupvm>2009-01-01</alkupvm>
                        <sijainti>
                            <Alue>
                                <gml:exterior>
                                    <gml:LinearRing>
                                        <gml:posList srsDimension="2">100.0 200.0 300.0 400.0 100.0 200.0</gml:posList>
                                    </gml:LinearRing>
                                </gml:exterior>
                            </Alue>
                        </sijainti>
                        <kuntatunnus>001</kuntatunnus>
                    </Kunta>
                </kunnat>
                """;
        var visitor = new CollectingVisitor();
        NlsGmlParser.parse(gmlStream(xml), visitor);

        assertEquals(1, visitor.kunnat.size());
        var coords = visitor.kunnat.getFirst().polygonCoordinates();
        assertEquals(3, coords.length);
        assertEquals(100.0, coords[0][0]);
        assertEquals(200.0, coords[0][1]);
    }

    @Test
    void parseKunta_3dCoordinates_dropsElevation() throws XMLStreamException {
        var xml = """
                <kunnat>
                    <Kunta gid="1" dimension="3">
                        <alkupvm>2009-01-01</alkupvm>
                        <sijainti>
                            <Alue>
                                <gml:exterior>
                                    <gml:LinearRing>
                                        <gml:posList srsDimension="3">100.0 200.0 5.0 300.0 400.0 10.0 100.0 200.0 5.0</gml:posList>
                                    </gml:LinearRing>
                                </gml:exterior>
                            </Alue>
                        </sijainti>
                        <kuntatunnus>001</kuntatunnus>
                    </Kunta>
                </kunnat>
                """;
        var visitor = new CollectingVisitor();
        NlsGmlParser.parse(gmlStream(xml), visitor);

        var coords = visitor.kunnat.getFirst().polygonCoordinates();
        assertEquals(3, coords.length);
        assertEquals(100.0, coords[0][0]);
        assertEquals(200.0, coords[0][1]);
        assertEquals(2, coords[0].length); // only easting and northing, no elevation
    }

    // === Tieviiva parsing ===

    @Test
    void parseTieviiva_fullFields_parsesCorrectly() throws XMLStreamException {
        var xml = """
                <tieviivat>
                    <Tieviiva gid="1186733682" dimension="3">
                        <sijaintitarkkuus>3000</sijaintitarkkuus>
                        <korkeustarkkuus>201</korkeustarkkuus>
                        <aineistolahde>1</aineistolahde>
                        <alkupvm>2018-06-05</alkupvm>
                        <kulkutapa>2</kulkutapa>
                        <sijainti>
                            <Murtoviiva>
                                <gml:posList srsDimension="3">232056.555 6678000.000 7.480 232100.000 6678050.000 8.000</gml:posList>
                            </Murtoviiva>
                        </sijainti>
                        <kohderyhma>25</kohderyhma>
                        <kohdeluokka>12141</kohdeluokka>
                        <tasosijainti>0</tasosijainti>
                        <valmiusaste>0</valmiusaste>
                        <paallyste>1</paallyste>
                        <yksisuuntaisuus>0</yksisuuntaisuus>
                        <hallinnollinenLuokka>3</hallinnollinenLuokka>
                        <nimi_suomi kieli="fin">Kuggöntie</nimi_suomi>
                        <nimi_ruotsi kieli="swe">Kuggövägen</nimi_ruotsi>
                        <minOsoitenumeroVasen>1</minOsoitenumeroVasen>
                        <maxOsoitenumeroVasen>99</maxOsoitenumeroVasen>
                        <minOsoitenumeroOikea>2</minOsoitenumeroOikea>
                        <maxOsoitenumeroOikea>100</maxOsoitenumeroOikea>
                        <kuntatunnus>445</kuntatunnus>
                    </Tieviiva>
                </tieviivat>
                """;
        var visitor = new CollectingVisitor();
        NlsGmlParser.parse(gmlStream(xml), visitor);

        assertEquals(1, visitor.tieviivat.size());
        var tv = visitor.tieviivat.getFirst();
        assertEquals(1186733682L, tv.gid());
        assertEquals(LocalDate.of(2018, 6, 5), tv.alkupvm());
        assertNull(tv.loppupvm());
        assertEquals(12141, tv.kohdeluokka());
        assertEquals(1, tv.paallyste());
        assertEquals(3, tv.hallinnollinenLuokka());
        assertEquals(0, tv.yksisuuntaisuus());
        assertEquals("Kuggöntie", tv.nameFi());
        assertEquals("Kuggövägen", tv.nameSv());
        assertNull(tv.nameSmn());
        assertNull(tv.nameSms());
        assertNull(tv.nameSme());
        assertEquals(1, tv.minAddressLeft());
        assertEquals(99, tv.maxAddressLeft());
        assertEquals(2, tv.minAddressRight());
        assertEquals(100, tv.maxAddressRight());
        assertEquals("445", tv.kuntatunnus());
        assertEquals(2, tv.lineCoordinates().length);
        assertEquals(232056.555, tv.lineCoordinates()[0][0], 1e-3);
    }

    @Test
    void parseTieviiva_zeroAddressRanges_becomesNull() throws XMLStreamException {
        var xml = """
                <tieviivat>
                    <Tieviiva gid="1" dimension="3">
                        <alkupvm>2018-06-05</alkupvm>
                        <sijainti>
                            <Murtoviiva>
                                <gml:posList srsDimension="3">100.0 200.0 0.0 300.0 400.0 0.0</gml:posList>
                            </Murtoviiva>
                        </sijainti>
                        <kohdeluokka>12141</kohdeluokka>
                        <paallyste>1</paallyste>
                        <yksisuuntaisuus>0</yksisuuntaisuus>
                        <minOsoitenumeroVasen>0</minOsoitenumeroVasen>
                        <maxOsoitenumeroVasen>0</maxOsoitenumeroVasen>
                        <minOsoitenumeroOikea>0</minOsoitenumeroOikea>
                        <maxOsoitenumeroOikea>0</maxOsoitenumeroOikea>
                    </Tieviiva>
                </tieviivat>
                """;
        var visitor = new CollectingVisitor();
        NlsGmlParser.parse(gmlStream(xml), visitor);

        var tv = visitor.tieviivat.getFirst();
        assertNull(tv.minAddressLeft());
        assertNull(tv.maxAddressLeft());
        assertNull(tv.minAddressRight());
        assertNull(tv.maxAddressRight());
    }

    @Test
    void parseTieviiva_minimalFields_parsesWithNulls() throws XMLStreamException {
        var xml = """
                <tieviivat>
                    <Tieviiva gid="1" dimension="2">
                        <alkupvm>2018-06-05</alkupvm>
                        <sijainti>
                            <Murtoviiva>
                                <gml:posList srsDimension="2">100.0 200.0 300.0 400.0</gml:posList>
                            </Murtoviiva>
                        </sijainti>
                        <kohdeluokka>12313</kohdeluokka>
                        <paallyste>0</paallyste>
                        <yksisuuntaisuus>0</yksisuuntaisuus>
                    </Tieviiva>
                </tieviivat>
                """;
        var visitor = new CollectingVisitor();
        NlsGmlParser.parse(gmlStream(xml), visitor);

        var tv = visitor.tieviivat.getFirst();
        assertNull(tv.hallinnollinenLuokka());
        assertNull(tv.nameFi());
        assertNull(tv.nameSv());
        assertNull(tv.kuntatunnus());
        assertNull(tv.minAddressLeft());
    }

    // === Osoitepiste parsing ===

    @Test
    void parseOsoitepiste_fullFields_parsesCorrectly() throws XMLStreamException {
        var xml = """
                <osoitepisteet>
                    <Osoitepiste gid="1754319417" dimension="2">
                        <sijaintitarkkuus>12500</sijaintitarkkuus>
                        <korkeustarkkuus>1</korkeustarkkuus>
                        <aineistolahde>1</aineistolahde>
                        <alkupvm>2016-10-11</alkupvm>
                        <suunta>0</suunta>
                        <sijainti>
                            <Piste><gml:pos srsDimension="2">231221.828 6677931.943</gml:pos></Piste>
                        </sijainti>
                        <kohderyhma>2</kohderyhma>
                        <kohdeluokka>96001</kohdeluokka>
                        <numero>427s</numero>
                        <nimi_suomi kieli="fin">Kuggö</nimi_suomi>
                        <nimi_ruotsi kieli="swe">Kuggö</nimi_ruotsi>
                        <kuntatunnus>445</kuntatunnus>
                    </Osoitepiste>
                </osoitepisteet>
                """;
        var visitor = new CollectingVisitor();
        NlsGmlParser.parse(gmlStream(xml), visitor);

        assertEquals(1, visitor.osoitepisteet.size());
        var op = visitor.osoitepisteet.getFirst();
        assertEquals(1754319417L, op.gid());
        assertEquals(LocalDate.of(2016, 10, 11), op.alkupvm());
        assertNull(op.loppupvm());
        assertEquals("427s", op.numero());
        assertEquals("Kuggö", op.nameFi());
        assertEquals("Kuggö", op.nameSv());
        assertNull(op.nameSmn());
        assertEquals("445", op.kuntatunnus());
        assertEquals(231221.828, op.pointEasting(), 1e-3);
        assertEquals(6677931.943, op.pointNorthing(), 1e-3);
    }

    @Test
    void parseOsoitepiste_3dCoordinates_dropsElevation() throws XMLStreamException {
        var xml = """
                <osoitepisteet>
                    <Osoitepiste gid="1" dimension="3">
                        <alkupvm>2016-10-11</alkupvm>
                        <sijainti>
                            <Piste><gml:pos srsDimension="3">100.0 200.0 50.0</gml:pos></Piste>
                        </sijainti>
                    </Osoitepiste>
                </osoitepisteet>
                """;
        var visitor = new CollectingVisitor();
        NlsGmlParser.parse(gmlStream(xml), visitor);

        var op = visitor.osoitepisteet.getFirst();
        assertEquals(100.0, op.pointEasting());
        assertEquals(200.0, op.pointNorthing());
    }

    @Test
    void parseOsoitepiste_minimalFields_parsesWithNulls() throws XMLStreamException {
        var xml = """
                <osoitepisteet>
                    <Osoitepiste gid="1" dimension="2">
                        <alkupvm>2016-10-11</alkupvm>
                        <sijainti>
                            <Piste><gml:pos srsDimension="2">100.0 200.0</gml:pos></Piste>
                        </sijainti>
                    </Osoitepiste>
                </osoitepisteet>
                """;
        var visitor = new CollectingVisitor();
        NlsGmlParser.parse(gmlStream(xml), visitor);

        var op = visitor.osoitepisteet.getFirst();
        assertNull(op.numero());
        assertNull(op.nameFi());
        assertNull(op.nameSv());
        assertNull(op.kuntatunnus());
    }

    // === Paikannimi parsing ===

    @Test
    void parsePaikannimi_fullFields_parsesCorrectly() throws XMLStreamException {
        var xml = """
                <paikannimet>
                    <Paikannimi gid="70304994" dimension="2">
                        <sijaintitarkkuus>0</sijaintitarkkuus>
                        <aineistolahde>1</aineistolahde>
                        <alkupvm>2016-05-04</alkupvm>
                        <teksti kieli="swe">Lilla Gulskär</teksti>
                        <suunta>0</suunta>
                        <dx>-33801</dx>
                        <dy>-95370</dy>
                        <sijainti>
                            <Piste><gml:pos srsDimension="2">228723.269 6674958.712</gml:pos></Piste>
                        </sijainti>
                        <kohderyhma>16</kohderyhma>
                        <kohdeluokka>35070</kohdeluokka>
                        <ladontatunnus>6111</ladontatunnus>
                        <versaalitieto>0</versaalitieto>
                        <nrKarttanimiId>70304994</nrKarttanimiId>
                    </Paikannimi>
                </paikannimet>
                """;
        var visitor = new CollectingVisitor();
        NlsGmlParser.parse(gmlStream(xml), visitor);

        assertEquals(1, visitor.paikannimet.size());
        var pn = visitor.paikannimet.getFirst();
        assertEquals(70304994L, pn.gid());
        assertEquals(LocalDate.of(2016, 5, 4), pn.alkupvm());
        assertNull(pn.loppupvm());
        assertEquals("Lilla Gulskär", pn.teksti());
        assertEquals("swe", pn.kieli());
        assertEquals(35070, pn.kohdeluokka());
        assertEquals(70304994L, pn.karttanimiId());
        assertEquals(228723.269, pn.pointEasting(), 1e-3);
        assertEquals(6674958.712, pn.pointNorthing(), 1e-3);
    }

    @Test
    void parsePaikannimi_withoutKarttanimiId_parsesAsNull() throws XMLStreamException {
        var xml = """
                <paikannimet>
                    <Paikannimi gid="1" dimension="2">
                        <alkupvm>2016-05-04</alkupvm>
                        <teksti kieli="fin">Testipaikka</teksti>
                        <sijainti>
                            <Piste><gml:pos srsDimension="2">100.0 200.0</gml:pos></Piste>
                        </sijainti>
                        <kohdeluokka>35010</kohdeluokka>
                    </Paikannimi>
                </paikannimet>
                """;
        var visitor = new CollectingVisitor();
        NlsGmlParser.parse(gmlStream(xml), visitor);

        var pn = visitor.paikannimet.getFirst();
        assertEquals("Testipaikka", pn.teksti());
        assertEquals("fin", pn.kieli());
        assertNull(pn.karttanimiId());
    }

    // === Feature type filtering ===

    @Test
    void parse_filterByType_onlyParsesRequestedTypes() throws XMLStreamException {
        var xml = """
                <kunnat>
                    <Kunta gid="1" dimension="2">
                        <alkupvm>2009-01-01</alkupvm>
                        <sijainti>
                            <Alue><gml:exterior><gml:LinearRing>
                                <gml:posList srsDimension="2">100.0 200.0 300.0 400.0 100.0 200.0</gml:posList>
                            </gml:LinearRing></gml:exterior></Alue>
                        </sijainti>
                        <kuntatunnus>001</kuntatunnus>
                    </Kunta>
                </kunnat>
                <paikannimet>
                    <Paikannimi gid="2" dimension="2">
                        <alkupvm>2016-05-04</alkupvm>
                        <teksti kieli="fin">Test</teksti>
                        <sijainti><Piste><gml:pos srsDimension="2">100.0 200.0</gml:pos></Piste></sijainti>
                        <kohdeluokka>35010</kohdeluokka>
                    </Paikannimi>
                </paikannimet>
                """;
        var visitor = new CollectingVisitor();
        NlsGmlParser.parse(gmlStream(xml), visitor, EnumSet.of(FeatureType.PAIKANNIMI));

        assertEquals(0, visitor.kunnat.size());
        assertEquals(1, visitor.paikannimet.size());
    }

    // === Error handling ===

    @Test
    void parse_malformedFeature_skipsAndContinues() throws XMLStreamException {
        var xml = """
                <paikannimet>
                    <Paikannimi gid="1" dimension="2">
                        <alkupvm>2016-05-04</alkupvm>
                    </Paikannimi>
                    <Paikannimi gid="2" dimension="2">
                        <alkupvm>2016-05-04</alkupvm>
                        <teksti kieli="fin">Valid</teksti>
                        <sijainti><Piste><gml:pos srsDimension="2">100.0 200.0</gml:pos></Piste></sijainti>
                        <kohdeluokka>35010</kohdeluokka>
                    </Paikannimi>
                </paikannimet>
                """;
        var visitor = new CollectingVisitor();
        NlsGmlParser.parse(gmlStream(xml), visitor);

        // First feature is malformed (missing teksti, kohdeluokka, sijainti), second is valid
        assertEquals(1, visitor.paikannimet.size());
        assertEquals("Valid", visitor.paikannimet.getFirst().teksti());
    }

    @Test
    void parse_visitorException_propagates() {
        var xml = """
                <paikannimet>
                    <Paikannimi gid="1" dimension="2">
                        <alkupvm>2016-05-04</alkupvm>
                        <teksti kieli="fin">Test</teksti>
                        <sijainti><Piste><gml:pos srsDimension="2">100.0 200.0</gml:pos></Piste></sijainti>
                        <kohdeluokka>35010</kohdeluokka>
                    </Paikannimi>
                </paikannimet>
                """;
        var failingVisitor = new CollectingVisitor() {
            @Override
            public void onPaikannimi(PaikannimiFeature feature) {
                throw new RuntimeException("DB write failed");
            }
        };
        var ex = assertThrows(RuntimeException.class,
                () -> NlsGmlParser.parse(gmlStream(xml), failingVisitor));
        assertEquals("DB write failed", ex.getMessage());
    }

    // === Empty document ===

    @Test
    void parse_emptyDocument_producesNoFeatures() throws XMLStreamException {
        var xml = "";
        var visitor = new CollectingVisitor();
        NlsGmlParser.parse(gmlStream(xml), visitor);

        assertEquals(0, visitor.kunnat.size());
        assertEquals(0, visitor.tieviivat.size());
        assertEquals(0, visitor.osoitepisteet.size());
        assertEquals(0, visitor.paikannimet.size());
    }

    @Test
    void parse_unrecognizedElements_ignored() throws XMLStreamException {
        var xml = """
                <harvatLouhikot>
                    <HarvaLouhikko gid="1" dimension="2">
                        <alkupvm>2018-06-05</alkupvm>
                    </HarvaLouhikko>
                </harvatLouhikot>
                """;
        var visitor = new CollectingVisitor();
        NlsGmlParser.parse(gmlStream(xml), visitor);

        assertEquals(0, visitor.kunnat.size());
        assertEquals(0, visitor.tieviivat.size());
        assertEquals(0, visitor.osoitepisteet.size());
        assertEquals(0, visitor.paikannimet.size());
    }

    // === Integration test with sample data ===

    @Test
    void parse_sampleDataFile_correctFeatureCounts() throws Exception {
        var sampleFile = Path.of("../../../SampleData/L3311R.xml");
        if (!Files.exists(sampleFile)) {
            // Skip if sample data not available (e.g., in CI)
            return;
        }

        var visitor = new CollectingVisitor();
        try (var input = new FileInputStream(sampleFile.toFile())) {
            NlsGmlParser.parse(input, visitor);
        }

        assertEquals(2, visitor.kunnat.size(), "Expected 2 Kunta features");
        assertEquals(83, visitor.tieviivat.size(), "Expected 83 Tieviiva features");
        assertEquals(10, visitor.osoitepisteet.size(), "Expected 10 Osoitepiste features");
        assertEquals(273, visitor.paikannimet.size(), "Expected 273 Paikannimi features");
    }

    @Test
    void parse_sampleDataFile_spotCheckKunta() throws Exception {
        var sampleFile = Path.of("../../../SampleData/L3311R.xml");
        if (!Files.exists(sampleFile)) {
            return;
        }

        var visitor = new CollectingVisitor();
        try (var input = new FileInputStream(sampleFile.toFile())) {
            NlsGmlParser.parse(input, visitor);
        }

        var kunta445 = visitor.kunnat.stream().filter(k -> k.gid() == 27696440L).findFirst().orElseThrow();
        assertEquals("445", kunta445.kuntatunnus());
        assertEquals(LocalDate.of(2009, 1, 1), kunta445.alkupvm());
        assertNull(kunta445.loppupvm());
        assertTrue(kunta445.polygonCoordinates().length > 2);
    }

    @Test
    void parse_sampleDataFile_spotCheckOsoitepiste() throws Exception {
        var sampleFile = Path.of("../../../SampleData/L3311R.xml");
        if (!Files.exists(sampleFile)) {
            return;
        }

        var visitor = new CollectingVisitor();
        try (var input = new FileInputStream(sampleFile.toFile())) {
            NlsGmlParser.parse(input, visitor);
        }

        var op = visitor.osoitepisteet.stream().filter(o -> o.gid() == 1754319417L).findFirst().orElseThrow();
        assertEquals("427s", op.numero());
        assertEquals("Kuggö", op.nameFi());
        assertEquals("Kuggö", op.nameSv());
        assertEquals("445", op.kuntatunnus());
        assertEquals(231221.828, op.pointEasting(), 1e-3);
        assertEquals(6677931.943, op.pointNorthing(), 1e-3);
    }
}

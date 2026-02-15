package net.pkhapps.idispatchx.gis.importer.parser;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MunicipalityJsonParserTest {

    private static final Path SAMPLE_DATA_FILE = Path.of("../../../SampleData/codelist_kunta_1_20250101.json");

    @Test
    void parse_validEntries_extractsCorrectly() throws IOException {
        var json = """
                {
                  "codeValue": "kunta_1_20250101",
                  "extensions": [],
                  "codes": [
                    {
                      "codeValue": "445",
                      "status": "VALID",
                      "prefLabel": {
                        "fi": "Parainen",
                        "sv": "Pargas",
                        "se": "Parainen",
                        "smn": "Parainen",
                        "sms": "Parainen",
                        "en": "Pargas"
                      }
                    },
                    {
                      "codeValue": "322",
                      "status": "VALID",
                      "prefLabel": {
                        "fi": "Kemiönsaari",
                        "sv": "Kimitoön",
                        "se": "Kemiönsaari",
                        "smn": "Kemiönsaari",
                        "sms": "Kemiönsaari",
                        "en": "Kimitoön"
                      }
                    }
                  ]
                }
                """;
        var entries = MunicipalityJsonParser.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, entries.size());

        var parainen = entries.stream().filter(e -> "445".equals(e.code())).findFirst().orElseThrow();
        assertEquals("Parainen", parainen.nameFi());
        assertEquals("Pargas", parainen.nameSv());
        assertEquals("Parainen", parainen.nameSme()); // "se" mapped to nameSme
        assertEquals("Parainen", parainen.nameSmn());
        assertEquals("Parainen", parainen.nameSms());
    }

    @Test
    void parse_invalidStatusFiltered() throws IOException {
        var json = """
                {
                  "codes": [
                    {
                      "codeValue": "999",
                      "status": "DEPRECATED",
                      "prefLabel": {"fi": "Old", "sv": "Old"}
                    },
                    {
                      "codeValue": "100",
                      "status": "VALID",
                      "prefLabel": {"fi": "Valid", "sv": "Valid"}
                    }
                  ]
                }
                """;
        var entries = MunicipalityJsonParser.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, entries.size());
        assertEquals("100", entries.getFirst().code());
    }

    @Test
    void parse_languageMapping_seToSme() throws IOException {
        var json = """
                {
                  "codes": [
                    {
                      "codeValue": "261",
                      "status": "VALID",
                      "prefLabel": {
                        "fi": "Kittilä",
                        "sv": "Kittilä",
                        "se": "Gihttel",
                        "smn": "Kittilä",
                        "sms": "Kittilä"
                      }
                    }
                  ]
                }
                """;
        var entries = MunicipalityJsonParser.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, entries.size());
        assertEquals("Gihttel", entries.getFirst().nameSme());
    }

    @Test
    void parse_extensionsIgnored() throws IOException {
        var json = """
                {
                  "extensions": [
                    {
                      "propertyType": "cross-reference",
                      "members": [{"code": "445", "relatedCode": "XYZ"}]
                    }
                  ],
                  "codes": [
                    {
                      "codeValue": "445",
                      "status": "VALID",
                      "prefLabel": {"fi": "Parainen", "sv": "Pargas"}
                    }
                  ]
                }
                """;
        var entries = MunicipalityJsonParser.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, entries.size());
    }

    @Test
    void parse_sampleDataFile_correctEntryCount() throws Exception {
        Assumptions.assumeTrue(Files.exists(SAMPLE_DATA_FILE),
                "Sample data file not found: " + SAMPLE_DATA_FILE.toAbsolutePath());

        try (var input = new FileInputStream(SAMPLE_DATA_FILE.toFile())) {
            var entries = MunicipalityJsonParser.parse(input);
            // Should have valid municipalities — Finland has ~310 municipalities
            assertTrue(entries.size() > 300, "Expected > 300 valid municipalities, got " + entries.size());
            assertTrue(entries.size() < 350, "Expected < 350 valid municipalities, got " + entries.size());

            // Spot check Parainen
            var parainen = entries.stream().filter(e -> "445".equals(e.code())).findFirst().orElseThrow();
            assertEquals("Parainen", parainen.nameFi());
            assertEquals("Pargas", parainen.nameSv());
        }
    }
}

package net.pkhapps.idispatchx.gis.importer.parser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.pkhapps.idispatchx.gis.importer.parser.model.MunicipalityEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Streaming JSON parser for municipality reference data from koodistot.suomi.fi.
 * Navigates directly to the {@code codes} array and extracts VALID municipality entries.
 */
public final class MunicipalityJsonParser {

    private static final Logger LOG = LoggerFactory.getLogger(MunicipalityJsonParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MunicipalityJsonParser() {
    }

    /**
     * Parses the municipality reference JSON and returns all valid municipality entries.
     *
     * @param input the JSON input stream
     * @return list of parsed municipality entries with status VALID
     * @throws IOException if parsing fails
     */
    public static List<MunicipalityEntry> parse(InputStream input) throws IOException {
        var entries = new ArrayList<MunicipalityEntry>();
        try (var parser = MAPPER.getFactory().createParser(input)) {
            navigateToCodesArray(parser);
            if (parser.currentToken() == JsonToken.START_ARRAY) {
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (parser.currentToken() == JsonToken.START_OBJECT) {
                        var entry = parseCodeEntry(parser);
                        if (entry != null) {
                            entries.add(entry);
                        }
                    }
                }
            }
        }
        LOG.info("Parsed {} valid municipality entries from JSON", entries.size());
        return entries;
    }

    private static void navigateToCodesArray(JsonParser parser) throws IOException {
        // Navigate to the top-level "codes" field
        while (parser.nextToken() != null) {
            if (parser.currentToken() == JsonToken.FIELD_NAME && "codes".equals(parser.currentName())) {
                parser.nextToken(); // move to START_ARRAY
                return;
            }
            // Skip nested objects/arrays that aren't "codes"
            if (parser.currentToken() == JsonToken.START_OBJECT || parser.currentToken() == JsonToken.START_ARRAY) {
                if (parser.currentToken() == JsonToken.START_ARRAY && parser.currentName() != null && !"codes".equals(parser.currentName())) {
                    parser.skipChildren();
                }
            }
        }
    }

    private static @Nullable MunicipalityEntry parseCodeEntry(JsonParser parser) throws IOException {
        String codeValue = null;
        String status = null;
        String nameFi = null;
        String nameSv = null;
        String nameSmn = null;
        String nameSms = null;
        String nameSme = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() == JsonToken.FIELD_NAME) {
                var fieldName = parser.currentName();
                parser.nextToken();
                switch (fieldName) {
                    case "codeValue" -> codeValue = parser.getText();
                    case "status" -> status = parser.getText();
                    case "prefLabel" -> {
                        if (parser.currentToken() == JsonToken.START_OBJECT) {
                            while (parser.nextToken() != JsonToken.END_OBJECT) {
                                if (parser.currentToken() == JsonToken.FIELD_NAME) {
                                    var lang = parser.currentName();
                                    parser.nextToken();
                                    switch (lang) {
                                        case "fi" -> nameFi = parser.getText();
                                        case "sv" -> nameSv = parser.getText();
                                        case "smn" -> nameSmn = parser.getText();
                                        case "sms" -> nameSms = parser.getText();
                                        case "se" -> nameSme = parser.getText(); // ISO 639-1 "se" → "sme"
                                        default -> { /* ignore other languages like "en" */ }
                                    }
                                }
                            }
                        }
                    }
                    default -> {
                        if (parser.currentToken() == JsonToken.START_OBJECT
                                || parser.currentToken() == JsonToken.START_ARRAY) {
                            parser.skipChildren();
                        }
                    }
                }
            }
        }

        if (!"VALID".equals(status)) {
            return null;
        }
        if (codeValue == null) {
            LOG.warn("Skipping VALID entry without codeValue");
            return null;
        }
        return new MunicipalityEntry(codeValue, nameFi, nameSv, nameSmn, nameSms, nameSme);
    }
}

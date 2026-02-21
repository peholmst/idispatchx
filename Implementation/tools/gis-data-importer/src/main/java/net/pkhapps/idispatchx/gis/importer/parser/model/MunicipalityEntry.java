package net.pkhapps.idispatchx.gis.importer.parser.model;

import org.jspecify.annotations.Nullable;

/**
 * Parsed municipality entry from the municipality reference JSON file.
 *
 * @param code    3-digit municipality code
 * @param nameFi  Finnish name
 * @param nameSv  Swedish name
 * @param nameSmn Inari Sami name
 * @param nameSms Skolt Sami name
 * @param nameSme Northern Sami name
 */
public record MunicipalityEntry(
        String code,
        @Nullable String nameFi,
        @Nullable String nameSv,
        @Nullable String nameSmn,
        @Nullable String nameSms,
        @Nullable String nameSme
) {
}

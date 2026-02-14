package net.pkhapps.idispatchx.gis.importer.parser.model;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Parsed Paikannimi (place name) feature from NLS GML data.
 *
 * @param gid           globally unique NLS feature identifier
 * @param alkupvm       feature creation/modification date
 * @param loppupvm      feature end/retirement date, or {@code null} if active
 * @param teksti        the place name text
 * @param kieli         language code (ISO 639: "fin", "swe", "smn", "sms", "sme")
 * @param kohdeluokka   place name class code
 * @param karttanimiId  map name register ID linking multilingual entries, or {@code null}
 * @param pointEasting  point easting coordinate in EPSG:3067
 * @param pointNorthing point northing coordinate in EPSG:3067
 */
public record PaikannimiFeature(
        long gid,
        LocalDate alkupvm,
        @Nullable LocalDate loppupvm,
        String teksti,
        String kieli,
        int kohdeluokka,
        @Nullable Long karttanimiId,
        double pointEasting,
        double pointNorthing
) {
}

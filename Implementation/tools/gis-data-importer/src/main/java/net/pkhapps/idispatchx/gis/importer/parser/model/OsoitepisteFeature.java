package net.pkhapps.idispatchx.gis.importer.parser.model;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Parsed Osoitepiste (address point) feature from NLS GML data.
 *
 * @param gid           globally unique NLS feature identifier
 * @param alkupvm       feature creation/modification date
 * @param loppupvm      feature end/retirement date, or {@code null} if active
 * @param numero        address number (may contain letters, e.g. "427s"), or {@code null}
 * @param nameFi        Finnish street name, or {@code null}
 * @param nameSv        Swedish street name, or {@code null}
 * @param nameSmn       Inari Sami name, or {@code null}
 * @param nameSms       Skolt Sami name, or {@code null}
 * @param nameSme       Northern Sami name, or {@code null}
 * @param kuntatunnus   municipality code, or {@code null}
 * @param pointEasting  point easting coordinate in EPSG:3067
 * @param pointNorthing point northing coordinate in EPSG:3067
 */
public record OsoitepisteFeature(
        long gid,
        LocalDate alkupvm,
        @Nullable LocalDate loppupvm,
        @Nullable String numero,
        @Nullable String nameFi,
        @Nullable String nameSv,
        @Nullable String nameSmn,
        @Nullable String nameSms,
        @Nullable String nameSme,
        @Nullable String kuntatunnus,
        double pointEasting,
        double pointNorthing
) {
}

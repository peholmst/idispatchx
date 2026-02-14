package net.pkhapps.idispatchx.gis.importer.parser.model;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Parsed Tieviiva (road segment) feature from NLS GML data.
 *
 * @param gid                 globally unique NLS feature identifier
 * @param alkupvm             feature creation/modification date
 * @param loppupvm            feature end/retirement date, or {@code null} if active
 * @param kohdeluokka         road class code
 * @param paallyste           surface type (0=unknown, 1=unpaved, 2=paved)
 * @param hallinnollinenLuokka administrative class, or {@code null} if not specified
 * @param yksisuuntaisuus     one-way direction (0=bidirectional, 1=forward, 2=backward)
 * @param nameFi              Finnish street name, or {@code null}
 * @param nameSv              Swedish street name, or {@code null}
 * @param nameSmn             Inari Sami name, or {@code null}
 * @param nameSms             Skolt Sami name, or {@code null}
 * @param nameSme             Northern Sami name, or {@code null}
 * @param minAddressLeft      min address number on left side, or {@code null} if none
 * @param maxAddressLeft      max address number on left side, or {@code null} if none
 * @param minAddressRight     min address number on right side, or {@code null} if none
 * @param maxAddressRight     max address number on right side, or {@code null} if none
 * @param kuntatunnus         municipality code, or {@code null}
 * @param lineCoordinates     road centerline coordinates as easting/northing pairs in EPSG:3067
 */
public record TieviivaFeature(
        long gid,
        LocalDate alkupvm,
        @Nullable LocalDate loppupvm,
        int kohdeluokka,
        int paallyste,
        @Nullable Integer hallinnollinenLuokka,
        int yksisuuntaisuus,
        @Nullable String nameFi,
        @Nullable String nameSv,
        @Nullable String nameSmn,
        @Nullable String nameSms,
        @Nullable String nameSme,
        @Nullable Integer minAddressLeft,
        @Nullable Integer maxAddressLeft,
        @Nullable Integer minAddressRight,
        @Nullable Integer maxAddressRight,
        @Nullable String kuntatunnus,
        double[][] lineCoordinates
) {
}

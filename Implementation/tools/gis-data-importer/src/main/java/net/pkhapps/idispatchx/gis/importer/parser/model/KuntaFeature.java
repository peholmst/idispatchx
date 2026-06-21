package net.pkhapps.idispatchx.gis.importer.parser.model;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Parsed Kunta (municipality boundary) feature from NLS GML data.
 *
 * @param gid                 globally unique NLS feature identifier
 * @param alkupvm             feature creation/modification date
 * @param loppupvm            feature end/retirement date, or {@code null} if active
 * @param kuntatunnus         3-digit municipality code
 * @param polygonCoordinates  boundary polygon coordinates as easting/northing pairs in EPSG:3067
 */
public record KuntaFeature(
        long gid,
        LocalDate alkupvm,
        @Nullable LocalDate loppupvm,
        String kuntatunnus,
        double[][] polygonCoordinates
) {
    public KuntaFeature {
        polygonCoordinates = copy(polygonCoordinates);
    }

    @Override
    public double[][] polygonCoordinates() {
        return copy(polygonCoordinates);
    }

    private static double[][] copy(double[][] coordinates) {
        var copied = new double[coordinates.length][];
        for (int i = 0; i < coordinates.length; i++) {
            copied[i] = coordinates[i].clone();
        }
        return copied;
    }
}

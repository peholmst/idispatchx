package net.pkhapps.idispatchx.gis.importer.db;

import org.jooq.Field;
import org.jooq.Geometry;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

/**
 * Type-safe jOOQ wrappers for PostGIS functions used by the GIS Data Importer.
 * <p>
 * Each method returns a jOOQ {@link Field} that can be used in type-safe query construction.
 * The function names are strings (unavoidable without a commercial jOOQ license or custom DSL),
 * but all parameter types and return types are checked at compile time.
 */
final class PostGisDsl {

    private PostGisDsl() {
    }

    /**
     * {@code ST_GeomFromText(wkt, srid)} — creates a geometry from Well-Known Text with the given SRID.
     */
    static Field<Geometry> stGeomFromText(String wkt, int srid) {
        return DSL.function("ST_GeomFromText", SQLDataType.GEOMETRY, DSL.val(wkt), DSL.val(srid));
    }

    /**
     * {@code ST_SetSRID(geom, srid)} — sets the SRID on an existing geometry.
     */
    static Field<Geometry> stSetSrid(Field<Geometry> geom, int srid) {
        return DSL.function("ST_SetSRID", SQLDataType.GEOMETRY, geom, DSL.val(srid));
    }

    /**
     * {@code ST_Multi(geom)} — wraps a geometry as a MULTI type.
     */
    static Field<Geometry> stMulti(Field<Geometry> geom) {
        return DSL.function("ST_Multi", SQLDataType.GEOMETRY, geom);
    }

    /**
     * {@code ST_Union(a, b)} — returns the geometric union of two geometries.
     */
    static Field<Geometry> stUnion(Field<Geometry> a, Field<Geometry> b) {
        return DSL.function("ST_Union", SQLDataType.GEOMETRY, a, b);
    }

    /**
     * {@code ST_Contains(a, b)} — returns true if geometry {@code a} contains geometry {@code b}.
     */
    static Field<Boolean> stContains(Field<Geometry> a, Field<Geometry> b) {
        return DSL.function("ST_Contains", SQLDataType.BOOLEAN, a, b);
    }

    /**
     * Builds a WKT POINT string from longitude and latitude.
     * WKT uses (longitude, latitude) ordering.
     */
    static String pointWkt(double latitude, double longitude) {
        return "POINT(" + longitude + " " + latitude + ")";
    }

    /**
     * Builds a WKT LINESTRING string from an array of [latitude, longitude] pairs.
     * WKT uses (longitude, latitude) ordering.
     */
    static String lineStringWkt(double[][] coords) {
        var sb = new StringBuilder("LINESTRING(");
        for (int i = 0; i < coords.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(coords[i][1]).append(' ').append(coords[i][0]);
        }
        sb.append(')');
        return sb.toString();
    }

    /**
     * Builds a WKT POLYGON string from an array of [latitude, longitude] pairs.
     * WKT uses (longitude, latitude) ordering.
     */
    static String polygonWkt(double[][] coords) {
        var sb = new StringBuilder("POLYGON((");
        for (int i = 0; i < coords.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(coords[i][1]).append(' ').append(coords[i][0]);
        }
        sb.append("))");
        return sb.toString();
    }
}

/**
 * Authentication and authorization handlers for the GIS Server.
 * <p>
 * This package provides Javalin handlers for:
 * <ul>
 *   <li>JWT token validation ({@link net.pkhapps.idispatchx.gis.server.auth.JwtAuthHandler})</li>
 *   <li>Role-based authorization ({@link net.pkhapps.idispatchx.gis.server.auth.RoleAuthHandler})</li>
 *   <li>Authentication context access ({@link net.pkhapps.idispatchx.gis.server.auth.AuthContext})</li>
 * </ul>
 */
@NullMarked
package net.pkhapps.idispatchx.gis.server.auth;

import org.jspecify.annotations.NullMarked;

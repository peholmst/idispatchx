package net.pkhapps.idispatchx.gis.server.auth;

import io.javalin.http.Context;
import net.pkhapps.idispatchx.common.auth.Role;
import net.pkhapps.idispatchx.common.auth.TokenClaims;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * Utility class for accessing authentication context from Javalin request context.
 * <p>
 * This class provides static methods to:
 * <ul>
 *   <li>Store validated token claims in the context</li>
 *   <li>Retrieve token claims from the context</li>
 *   <li>Check user roles and permissions</li>
 * </ul>
 */
public final class AuthContext {

    /**
     * Context attribute key for storing token claims.
     */
    static final String CLAIMS_ATTRIBUTE = "auth.tokenClaims";

    private AuthContext() {
        // Utility class
    }

    /**
     * Stores validated token claims in the request context.
     *
     * @param ctx    the Javalin context
     * @param claims the validated token claims
     */
    public static void setClaims(Context ctx, TokenClaims claims) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(claims, "claims must not be null");
        ctx.attribute(CLAIMS_ATTRIBUTE, claims);
    }

    /**
     * Retrieves the token claims from the request context.
     *
     * @param ctx the Javalin context
     * @return the token claims, or null if not authenticated
     */
    public static @Nullable TokenClaims getClaims(Context ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return ctx.attribute(CLAIMS_ATTRIBUTE);
    }

    /**
     * Retrieves the token claims, throwing if not authenticated.
     *
     * @param ctx the Javalin context
     * @return the token claims
     * @throws IllegalStateException if the request is not authenticated
     */
    public static TokenClaims requireClaims(Context ctx) {
        var claims = getClaims(ctx);
        if (claims == null) {
            throw new IllegalStateException("Request is not authenticated");
        }
        return claims;
    }

    /**
     * Returns the authenticated user's subject (user ID).
     *
     * @param ctx the Javalin context
     * @return the subject, or null if not authenticated
     */
    public static @Nullable String getSubject(Context ctx) {
        var claims = getClaims(ctx);
        return claims != null ? claims.subject() : null;
    }

    /**
     * Returns the authenticated user's roles.
     *
     * @param ctx the Javalin context
     * @return the user's roles, or empty set if not authenticated
     */
    public static Set<Role> getRoles(Context ctx) {
        var claims = getClaims(ctx);
        return claims != null ? claims.roles() : Set.of();
    }

    /**
     * Checks if the authenticated user has the specified role.
     *
     * @param ctx  the Javalin context
     * @param role the role to check
     * @return true if the user has the role
     */
    public static boolean hasRole(Context ctx, Role role) {
        var claims = getClaims(ctx);
        return claims != null && claims.hasRole(role);
    }

    /**
     * Checks if the authenticated user has any of the specified roles.
     *
     * @param ctx   the Javalin context
     * @param roles the roles to check
     * @return true if the user has at least one of the roles
     */
    public static boolean hasAnyRole(Context ctx, Role... roles) {
        var claims = getClaims(ctx);
        return claims != null && claims.hasAnyRole(roles);
    }

    /**
     * Checks if the request is authenticated.
     *
     * @param ctx the Javalin context
     * @return true if the request has valid authentication
     */
    public static boolean isAuthenticated(Context ctx) {
        return getClaims(ctx) != null;
    }
}

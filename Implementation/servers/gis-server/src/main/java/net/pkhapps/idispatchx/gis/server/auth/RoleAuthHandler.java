package net.pkhapps.idispatchx.gis.server.auth;

import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.Handler;
import io.javalin.http.UnauthorizedResponse;
import net.pkhapps.idispatchx.common.auth.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;

/**
 * Javalin handler that enforces role-based access control.
 * <p>
 * This handler checks if the authenticated user has at least one of the
 * required roles. It must be used after {@link JwtAuthHandler} in the
 * handler chain.
 * <p>
 * If the user is not authenticated, responds with HTTP 401 Unauthorized.
 * If the user lacks required roles, responds with HTTP 403 Forbidden.
 * <p>
 * Usage:
 * <pre>
 * // Require Dispatcher role
 * app.get("/api/dispatch/*", ctx -> {...}, RoleAuthHandler.requireRole(Role.DISPATCHER));
 *
 * // Require any of multiple roles
 * app.get("/api/data/*", ctx -> {...}, RoleAuthHandler.requireAnyRole(Role.DISPATCHER, Role.OBSERVER));
 * </pre>
 */
public final class RoleAuthHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(RoleAuthHandler.class);

    private final Set<Role> requiredRoles;

    /**
     * Creates a new role authorization handler.
     *
     * @param requiredRoles the roles that grant access (at least one required)
     */
    private RoleAuthHandler(Set<Role> requiredRoles) {
        if (requiredRoles.isEmpty()) {
            throw new IllegalArgumentException("At least one role must be required");
        }
        this.requiredRoles = Set.copyOf(requiredRoles);
    }

    /**
     * Creates a handler that requires a specific role.
     *
     * @param role the required role
     * @return the role authorization handler
     */
    public static RoleAuthHandler requireRole(Role role) {
        Objects.requireNonNull(role, "role must not be null");
        return new RoleAuthHandler(Set.of(role));
    }

    /**
     * Creates a handler that requires any of the specified roles.
     *
     * @param roles the roles that grant access (at least one required)
     * @return the role authorization handler
     */
    public static RoleAuthHandler requireAnyRole(Role... roles) {
        if (roles.length == 0) {
            throw new IllegalArgumentException("At least one role must be specified");
        }
        return new RoleAuthHandler(Set.of(roles));
    }

    @Override
    public void handle(Context ctx) throws Exception {
        var claims = AuthContext.getClaims(ctx);

        if (claims == null) {
            log.debug("Unauthenticated request attempted to access {}", ctx.path());
            throw new UnauthorizedResponse("Authentication required");
        }

        boolean hasRole = claims.roles().stream()
                .anyMatch(requiredRoles::contains);

        if (!hasRole) {
            log.debug("User {} lacks required roles {} for {}",
                    claims.subject(), requiredRoles, ctx.path());
            throw new ForbiddenResponse("Insufficient permissions");
        }

        log.debug("User {} authorized for {} with roles {}",
                claims.subject(), ctx.path(), claims.roles());
    }

    /**
     * Returns the roles that grant access to this handler.
     *
     * @return the required roles
     */
    public Set<Role> getRequiredRoles() {
        return requiredRoles;
    }
}

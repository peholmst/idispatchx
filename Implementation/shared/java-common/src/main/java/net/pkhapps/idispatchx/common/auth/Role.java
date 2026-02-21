package net.pkhapps.idispatchx.common.auth;

import java.util.Arrays;
import java.util.Optional;

/**
 * System roles for iDispatchX users.
 * <p>
 * Roles are assigned to users via the OIDC provider and included in JWT tokens.
 */
public enum Role {

    /**
     * Dispatcher role - full access to CAD and GIS functionality.
     */
    DISPATCHER("Dispatcher"),

    /**
     * Observer role - read-only access to CAD and GIS functionality.
     */
    OBSERVER("Observer"),

    /**
     * Admin role - access to administrative functions.
     */
    ADMIN("Admin"),

    /**
     * Station role - access to station alert functionality.
     */
    STATION("Station"),

    /**
     * Unit role - access to mobile unit functionality.
     */
    UNIT("Unit");

    private final String claimValue;

    Role(String claimValue) {
        this.claimValue = claimValue;
    }

    /**
     * Returns the role claim value as it appears in JWT tokens.
     *
     * @return the claim value
     */
    public String getClaimValue() {
        return claimValue;
    }

    /**
     * Finds a role by its claim value.
     *
     * @param claimValue the claim value to search for
     * @return the matching role, or empty if not found
     */
    public static Optional<Role> fromClaimValue(String claimValue) {
        return Arrays.stream(values())
                .filter(role -> role.claimValue.equalsIgnoreCase(claimValue))
                .findFirst();
    }
}

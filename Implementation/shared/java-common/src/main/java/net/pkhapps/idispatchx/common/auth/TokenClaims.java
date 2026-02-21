package net.pkhapps.idispatchx.common.auth;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Parsed and validated claims from a JWT access token.
 *
 * @param subject      the subject (user identifier) from the 'sub' claim
 * @param issuer       the issuer from the 'iss' claim
 * @param sessionId    the session ID from the 'sid' claim, or null if not present
 * @param roles        the user's roles extracted from the token
 * @param expiresAt    the token expiration time from the 'exp' claim
 * @param issuedAt     the token issue time from the 'iat' claim, or null if not present
 */
public record TokenClaims(
        String subject,
        String issuer,
        @Nullable String sessionId,
        Set<Role> roles,
        Instant expiresAt,
        @Nullable Instant issuedAt
) {

    /**
     * Creates token claims with validation.
     */
    public TokenClaims {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(issuer, "issuer must not be null");
        Objects.requireNonNull(roles, "roles must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");

        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (issuer.isBlank()) {
            throw new IllegalArgumentException("issuer must not be blank");
        }

        // Make roles immutable
        roles = Set.copyOf(roles);
    }

    /**
     * Checks if the token has expired.
     *
     * @return true if the token has expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if the user has the specified role.
     *
     * @param role the role to check
     * @return true if the user has the role
     */
    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    /**
     * Checks if the user has any of the specified roles.
     *
     * @param requiredRoles the roles to check
     * @return true if the user has at least one of the roles
     */
    public boolean hasAnyRole(Role... requiredRoles) {
        for (Role role : requiredRoles) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }
}

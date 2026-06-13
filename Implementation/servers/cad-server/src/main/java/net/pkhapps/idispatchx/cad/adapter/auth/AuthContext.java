package net.pkhapps.idispatchx.cad.adapter.auth;

import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import net.pkhapps.idispatchx.common.auth.TokenClaims;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Utility class for storing and retrieving validated JWT claims in a Javalin request context.
 * <p>
 * The JWT auth before-handler places validated claims into the context; controllers and
 * role checks retrieve them from here.
 */
public final class AuthContext {

    static final String CLAIMS_ATTRIBUTE = "auth.tokenClaims";

    private AuthContext() {}

    /**
     * Stores validated token claims in the request context.
     */
    public static void setClaims(Context ctx, TokenClaims claims) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(claims, "claims must not be null");
        ctx.attribute(CLAIMS_ATTRIBUTE, claims);
    }

    /**
     * Retrieves the token claims from the request context, or {@code null} if not authenticated.
     */
    public static @Nullable TokenClaims getClaims(Context ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return ctx.attribute(CLAIMS_ATTRIBUTE);
    }

    /**
     * Retrieves the token claims, throwing {@link UnauthorizedResponse} if not authenticated.
     */
    public static TokenClaims requireClaims(Context ctx) {
        var claims = getClaims(ctx);
        if (claims == null) {
            throw new UnauthorizedResponse("Authentication required");
        }
        return claims;
    }
}

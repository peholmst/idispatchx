package net.pkhapps.idispatchx.cad.adapter.primary.rest.shared;

import io.javalin.http.Context;
import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.common.api.ValidationException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Extracts and validates the {@code X-Command-Id} header from Javalin request contexts.
 * <p>
 * The header is required for all mutating (non-GET) endpoints. It must contain a valid UUID
 * (any version) to be accepted by the {@link CommandId} domain type.
 */
public final class CommandIdExtractor {

    static final String HEADER_NAME = "X-Command-Id";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private CommandIdExtractor() {}

    /**
     * Extracts and validates the {@code X-Command-Id} header.
     *
     * @param ctx the Javalin context
     * @return the parsed CommandId
     * @throws ValidationException if the header is missing or not a valid UUID
     */
    public static CommandId extract(Context ctx) {
        var headerValue = ctx.header(HEADER_NAME);
        if (headerValue == null || headerValue.isBlank()) {
            throw new ValidationException(CadErrorCode.VALIDATION_ERROR,
                    HEADER_NAME + " header is required");
        }
        var trimmed = headerValue.trim();
        if (!UUID_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException(CadErrorCode.VALIDATION_ERROR,
                    HEADER_NAME + " must be a valid UUID (e.g. 550e8400-e29b-41d4-a716-446655440000)");
        }
        return new CommandId(trimmed);
    }

    /**
     * Derives a deterministic secondary {@link CommandId} from a base ID and a discriminator suffix.
     * <p>
     * Used when a single REST request must dispatch multiple commands (e.g. PATCH that updates
     * both call details and outcome). The derived ID is deterministic, so retries with the same
     * base ID produce the same derived ID — preserving idempotency for sub-commands.
     *
     * @param base   the primary command ID from the request header
     * @param suffix a short discriminator label (e.g. {@code "set-outcome"})
     * @return a stable CommandId derived from base + suffix
     */
    public static CommandId derive(CommandId base, String suffix) {
        var derived = UUID.nameUUIDFromBytes(
                (base.value() + ":" + suffix).getBytes(StandardCharsets.UTF_8));
        return new CommandId(derived.toString());
    }
}

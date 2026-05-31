package net.pkhapps.idispatchx.cad.domain.model.incident;

import com.fasterxml.jackson.databind.JsonNode;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.common.auth.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable log entry on an {@link Incident}.
 * <p>
 * Log entries are either generated automatically by the system ({@link AutomaticEntry})
 * or manually authored by a dispatcher ({@link ManualEntry}).
 */
public sealed interface IncidentLogEntry permits IncidentLogEntry.AutomaticEntry, IncidentLogEntry.ManualEntry {

    /**
     * A system-generated log entry recording structured change data.
     * <p>
     * The {@code dispatcher} field is never null. Use {@link UserId#SYSTEM} when the system
     * alone triggered the change.
     *
     * @param id           the log entry ID
     * @param logTimestamp the time the entry was recorded (UTC)
     * @param dispatcher   the user (or {@link UserId#SYSTEM}) associated with the change
     * @param changeData   structured JSON describing the change
     */
    record AutomaticEntry(
            IncidentLogEntryId id,
            Instant logTimestamp,
            UserId dispatcher,
            JsonNode changeData
    ) implements IncidentLogEntry {

        public AutomaticEntry {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(logTimestamp, "logTimestamp must not be null");
            Objects.requireNonNull(dispatcher, "dispatcher must not be null");
            Objects.requireNonNull(changeData, "changeData must not be null");
        }
    }

    /**
     * A dispatcher-authored log entry with free-form text.
     * <p>
     * The {@code dispatcher} must always be the human user who authored the entry.
     *
     * @param id           the log entry ID
     * @param logTimestamp the time the entry was recorded (UTC)
     * @param dispatcher   the human dispatcher who authored this entry
     * @param description  the free-form text description (max 1000 UTF-8 chars)
     */
    record ManualEntry(
            IncidentLogEntryId id,
            Instant logTimestamp,
            UserId dispatcher,
            Description description
    ) implements IncidentLogEntry {

        public ManualEntry {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(logTimestamp, "logTimestamp must not be null");
            Objects.requireNonNull(dispatcher, "dispatcher must not be null");
            Objects.requireNonNull(description, "description must not be null");
        }
    }
}

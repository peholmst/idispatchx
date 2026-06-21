package net.pkhapps.idispatchx.cad.port.secondary.archive;

import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;

/**
 * Secondary port for scheduling archival of completed calls and incidents.
 */
public interface ArchivePort {

    /**
     * Schedules archival of a call that is not linked to any incident.
     * <p>
     * Per the Availability NFR, this method must not be called within the critical
     * WAL-before-state path. If the port is unavailable, the failure must be logged
     * and the operation must continue.
     *
     * @param callId the ID of the unlinked call to archive
     */
    void scheduleUnlinkedCallArchival(CallId callId);

    /**
     * Returns {@code true} if the archive backend is currently reachable and healthy.
     * <p>
     * Called periodically by {@link net.pkhapps.idispatchx.cad.adapter.broadcast.ArchiveHealthMonitor}
     * to detect transitions between available and unavailable states. Implementations
     * should perform a lightweight probe (e.g., a single-row query). This method must
     * not throw — implementations must catch internal errors and return {@code false}.
     */
    boolean isAvailable();
}

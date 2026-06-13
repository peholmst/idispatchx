package net.pkhapps.idispatchx.cad.port.secondary.archive;

import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op stub for {@link ArchivePort}.
 * <p>
 * Logs a warning and takes no action. The actual archival implementation is out of scope.
 */
public class NoOpArchivePort implements ArchivePort {

    private static final Logger log = LoggerFactory.getLogger(NoOpArchivePort.class);

    @Override
    public void scheduleUnlinkedCallArchival(CallId callId) {
        log.warn("ArchivePort is not implemented: unlinked call archival skipped for callId={}", callId);
    }
}

package net.pkhapps.idispatchx.cad.adapter.broadcast;

import net.pkhapps.idispatchx.cad.port.secondary.archive.ArchivePort;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically probes the archive backend and broadcasts {@code system.status_changed}
 * to all connected dispatcher sessions when the availability transitions.
 * <p>
 * Also exposes the current availability via {@link #isCadArchiveAvailable()} so that
 * newly connecting clients can receive the correct state in the {@code connected} event
 * without waiting for the next poll cycle.
 * <p>
 * Scheduling must be started by the caller via {@link #start(ScheduledExecutorService, long)}.
 * The monitor assumes the initial state is available ({@code true}) and will broadcast
 * a transition on the first poll if the archive is already down.
 */
public final class ArchiveHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(ArchiveHealthMonitor.class);

    private final ArchivePort archivePort;
    private final DispatcherBroadcastService broadcastService;
    private final WalPort walPort;
    private final AtomicBoolean available = new AtomicBoolean(true);

    public ArchiveHealthMonitor(ArchivePort archivePort,
                                DispatcherBroadcastService broadcastService,
                                WalPort walPort) {
        this.archivePort = Objects.requireNonNull(archivePort, "archivePort must not be null");
        this.broadcastService = Objects.requireNonNull(broadcastService, "broadcastService must not be null");
        this.walPort = Objects.requireNonNull(walPort, "walPort must not be null");
    }

    /**
     * Returns the current archive availability as tracked by the last health probe.
     * Safe to call from any thread.
     */
    public boolean isCadArchiveAvailable() {
        return available.get();
    }

    /**
     * Schedules the periodic health probe on the given executor.
     *
     * @param scheduler        the executor to schedule on
     * @param intervalSeconds  probe interval in seconds
     */
    public void start(ScheduledExecutorService scheduler, long intervalSeconds) {
        scheduler.scheduleAtFixedRate(this::probe, 0, intervalSeconds, TimeUnit.SECONDS);
        log.info("ArchiveHealthMonitor started with interval={}s", intervalSeconds);
    }

    private void probe() {
        boolean nowAvailable;
        try {
            nowAvailable = archivePort.isAvailable();
        } catch (Exception e) {
            log.warn("Archive health probe threw unexpectedly: {}", e.getMessage());
            nowAvailable = false;
        }

        boolean wasAvailable = available.getAndSet(nowAvailable);
        if (nowAvailable != wasAvailable) {
            log.info("Archive availability changed: {} → {}", wasAvailable, nowAvailable);
            broadcastService.sendSystemStatusChanged(
                    walPort.currentSequenceNumber(),
                    Instant.now(),
                    nowAvailable
            );
        }
    }
}

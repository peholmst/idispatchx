package net.pkhapps.idispatchx.cad.port.secondary.clock;

import java.time.Instant;

/**
 * Port for obtaining the current time.
 * <p>
 * Abstracted for testability - tests can inject a controllable clock.
 */
public interface ClockPort {

    /**
     * Returns the current timestamp in UTC.
     */
    Instant now();
}

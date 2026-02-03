package net.pkhapps.idispatchx.cad.application.handler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;

/**
 * A handle to acquired locks that can be released via {@link AutoCloseable}.
 * <p>
 * Locks are released in reverse order of acquisition when {@link #close()} is called.
 */
public final class LockHandle implements AutoCloseable {

    private final List<Lock> locks;
    private boolean closed = false;

    LockHandle(List<Lock> locks) {
        this.locks = Objects.requireNonNull(locks, "locks must not be null");
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        // Release in reverse order
        for (int i = locks.size() - 1; i >= 0; i--) {
            locks.get(i).unlock();
        }
    }
}

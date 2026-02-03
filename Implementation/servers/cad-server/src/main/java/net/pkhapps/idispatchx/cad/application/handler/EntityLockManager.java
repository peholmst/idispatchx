package net.pkhapps.idispatchx.cad.application.handler;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages fine-grained locks at the aggregate root level.
 * <p>
 * Thread-safety for entities is achieved through external synchronization using this manager.
 * Lock keys are sorted before acquisition to ensure consistent ordering and prevent deadlocks.
 */
public final class EntityLockManager {

    private final ConcurrentHashMap<LockKey, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Acquires locks for all keys in the given scope.
     * <p>
     * Keys are sorted before acquisition to prevent deadlocks when multiple
     * aggregates need to be locked.
     *
     * @param scope the lock scope defining which aggregates to lock
     * @return a handle that releases the locks when closed
     */
    public LockHandle acquire(LockScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");

        // Sort keys to ensure consistent ordering (prevents deadlocks)
        var orderedKeys = scope.keys().stream()
                .sorted()
                .toList();

        var acquiredLocks = new ArrayList<Lock>();
        try {
            for (var key : orderedKeys) {
                var lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
                lock.lock();
                acquiredLocks.add(lock);
            }
            return new LockHandle(acquiredLocks);
        } catch (Exception e) {
            // If anything goes wrong during acquisition, release what we have
            for (int i = acquiredLocks.size() - 1; i >= 0; i--) {
                acquiredLocks.get(i).unlock();
            }
            throw e;
        }
    }
}

package net.pkhapps.idispatchx.cad.application.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EntityLockManagerTest {

    private EntityLockManager lockManager;

    @BeforeEach
    void setUp() {
        lockManager = new EntityLockManager();
    }

    @Test
    void acquire_singleKey_acquiresAndReleasesLock() {
        var scope = LockScope.of("incident", "123");

        try (var handle = lockManager.acquire(scope)) {
            assertNotNull(handle);
        }
        // Lock should be released, can acquire again
        try (var handle = lockManager.acquire(scope)) {
            assertNotNull(handle);
        }
    }

    @Test
    void acquire_multipleKeys_acquiresInSortedOrder() {
        // Keys provided in reverse order should still work (sorted internally)
        var scope = LockScope.of(
                new LockKey("unit", "456"),
                new LockKey("incident", "123")
        );

        try (var handle = lockManager.acquire(scope)) {
            assertNotNull(handle);
        }
    }

    @Test
    void acquire_sameKeyTwiceFromSameThread_allowsReentrant() {
        var scope = LockScope.of("incident", "123");

        try (var outer = lockManager.acquire(scope)) {
            // ReentrantLock allows same thread to acquire again
            try (var inner = lockManager.acquire(scope)) {
                assertNotNull(inner);
            }
        }
    }

    @Test
    void acquire_concurrentAccessToSameKey_serializes() throws Exception {
        var scope = LockScope.of("incident", "123");
        var executionOrder = Collections.synchronizedList(new ArrayList<String>());
        var latch = new CountDownLatch(2);

        var thread1 = new Thread(() -> {
            try (var handle = lockManager.acquire(scope)) {
                executionOrder.add("thread1-start");
                Thread.sleep(50); // Hold lock briefly
                executionOrder.add("thread1-end");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });

        var thread2 = new Thread(() -> {
            try {
                Thread.sleep(10); // Ensure thread1 gets lock first
                try (var handle = lockManager.acquire(scope)) {
                    executionOrder.add("thread2-start");
                    executionOrder.add("thread2-end");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });

        thread1.start();
        thread2.start();
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Thread2 should only start after thread1 ends
        assertEquals(List.of("thread1-start", "thread1-end", "thread2-start", "thread2-end"),
                executionOrder);
    }

    @Test
    void acquire_differentKeys_allowsConcurrent() throws Exception {
        var scope1 = LockScope.of("incident", "123");
        var scope2 = LockScope.of("incident", "456");
        var concurrentCount = new AtomicInteger(0);
        var maxConcurrent = new AtomicInteger(0);
        var latch = new CountDownLatch(2);

        java.util.function.Function<LockScope, Runnable> factory = scope -> () -> {
            try (var handle = lockManager.acquire(scope)) {
                int current = concurrentCount.incrementAndGet();
                maxConcurrent.updateAndGet(max -> Math.max(max, current));
                Thread.sleep(50);
                concurrentCount.decrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        };

        var thread1 = new Thread(factory.apply(scope1));
        var thread2 = new Thread(factory.apply(scope2));

        thread1.start();
        thread2.start();
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Both threads should have run concurrently
        assertEquals(2, maxConcurrent.get());
    }

    @Test
    void acquire_multipleKeysPreventDeadlock() throws Exception {
        // Two threads acquiring same keys in different order should not deadlock
        // because EntityLockManager sorts keys before acquisition
        var key1 = new LockKey("a", "1");
        var key2 = new LockKey("b", "2");

        var scope1 = LockScope.of(key1, key2); // Will be sorted to [a:1, b:2]
        var scope2 = LockScope.of(key2, key1); // Will also be sorted to [a:1, b:2]

        var latch = new CountDownLatch(2);
        var completed = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> {
                try (var handle = lockManager.acquire(scope1)) {
                    Thread.sleep(10);
                    completed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try (var handle = lockManager.acquire(scope2)) {
                    Thread.sleep(10);
                    completed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });

            // If there's a deadlock, this will timeout
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Deadlock detected");
            assertEquals(2, completed.get());
        } finally {
            executor.shutdownNow();
        }
    }
}

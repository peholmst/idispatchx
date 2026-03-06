package net.pkhapps.idispatchx.gis.server.service.tile;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class TileCacheTest {

    @Test
    void defaultMaxSizeIs1000() {
        var cache = new TileCache();
        assertEquals(1000, cache.getMaxSize());
    }

    @Test
    void rejectsMaxSizeZero() {
        assertThrows(IllegalArgumentException.class, () -> new TileCache(0));
    }

    @Test
    void rejectsMaxSizeNegative() {
        assertThrows(IllegalArgumentException.class, () -> new TileCache(-1));
    }

    @Test
    void getMissReturnsNull() {
        var cache = new TileCache(10);
        assertNull(cache.get("terrain", 5, 100, 200));
    }

    @Test
    void putAndGetReturnsData() {
        var cache = new TileCache(10);
        var data = new byte[]{1, 2, 3};
        cache.put("terrain", 5, 100, 200, data);
        assertArrayEquals(data, cache.get("terrain", 5, 100, 200));
    }

    @Test
    void differentColumnsDontCollide() {
        var cache = new TileCache(10);
        var data1 = new byte[]{1};
        var data2 = new byte[]{2};
        cache.put("terrain", 5, 100, 200, data1);
        cache.put("terrain", 5, 100, 201, data2);
        assertArrayEquals(data1, cache.get("terrain", 5, 100, 200));
        assertArrayEquals(data2, cache.get("terrain", 5, 100, 201));
    }

    @Test
    void differentLayersDontCollide() {
        var cache = new TileCache(10);
        var data1 = new byte[]{1};
        var data2 = new byte[]{2};
        cache.put("terrain", 5, 100, 200, data1);
        cache.put("roads", 5, 100, 200, data2);
        assertArrayEquals(data1, cache.get("terrain", 5, 100, 200));
        assertArrayEquals(data2, cache.get("roads", 5, 100, 200));
    }

    @Test
    void lruEvictionRemovesOldestEntry() {
        var cache = new TileCache(3);

        cache.put("layer", 0, 0, 0, new byte[]{0});
        cache.put("layer", 0, 0, 1, new byte[]{1});
        cache.put("layer", 0, 0, 2, new byte[]{2});

        assertEquals(3, cache.size());

        // Access entry 0 to make it recently used
        cache.get("layer", 0, 0, 0);

        // Add a 4th entry — entry 1 (least recently used) should be evicted
        cache.put("layer", 0, 0, 3, new byte[]{3});

        assertEquals(3, cache.size());
        assertNotNull(cache.get("layer", 0, 0, 0)); // still present (was accessed)
        assertNull(cache.get("layer", 0, 0, 1));    // evicted
        assertNotNull(cache.get("layer", 0, 0, 2)); // still present
        assertNotNull(cache.get("layer", 0, 0, 3)); // just added
    }

    @Test
    void sizeTracksCacheEntries() {
        var cache = new TileCache(10);
        assertEquals(0, cache.size());
        cache.put("layer", 0, 0, 0, new byte[]{});
        assertEquals(1, cache.size());
        cache.put("layer", 0, 0, 1, new byte[]{});
        assertEquals(2, cache.size());
    }

    @Test
    void clearRemovesAllEntries() {
        var cache = new TileCache(10);
        cache.put("layer", 0, 0, 0, new byte[]{});
        cache.put("layer", 0, 0, 1, new byte[]{});
        cache.clear();
        assertEquals(0, cache.size());
        assertNull(cache.get("layer", 0, 0, 0));
    }

    @Test
    void hitAndMissStatsAreTracked() {
        var cache = new TileCache(10);
        cache.put("layer", 0, 0, 0, new byte[]{1});

        cache.get("layer", 0, 0, 0); // hit
        cache.get("layer", 0, 0, 1); // miss
        cache.get("layer", 0, 0, 1); // miss

        assertEquals(1, cache.getHits());
        assertEquals(2, cache.getMisses());
    }

    @Test
    void threadSafetyUnderConcurrentAccess() throws InterruptedException {
        var cache = new TileCache(500);
        int threadCount = 10;
        int opsPerThread = 100;
        var latch = new CountDownLatch(threadCount);
        var executor = Executors.newFixedThreadPool(threadCount);
        var errors = new ArrayList<Throwable>();

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        int col = threadId * opsPerThread + i;
                        cache.put("layer", 0, 0, col, new byte[]{(byte) col});
                        cache.get("layer", 0, 0, col);
                    }
                } catch (Throwable e) {
                    synchronized (errors) {
                        errors.add(e);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        assertTrue(errors.isEmpty(), "Expected no errors but got: " + errors);
    }
}

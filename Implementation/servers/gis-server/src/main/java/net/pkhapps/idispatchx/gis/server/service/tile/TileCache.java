package net.pkhapps.idispatchx.gis.server.service.tile;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU (Least Recently Used) cache for resampled tile data.
 * <p>
 * Caches byte arrays keyed by layer name, zoom level, row, and column.
 * The cache uses a synchronized LinkedHashMap in access-order mode to
 * implement LRU eviction when the maximum size is exceeded.
 * <p>
 * This class is thread-safe.
 */
public final class TileCache {

    /**
     * Default maximum number of entries in the cache.
     */
    public static final int DEFAULT_MAX_SIZE = 1000;

    private final int maxSize;
    private final Map<CacheKey, byte[]> cache;

    private long hits = 0;
    private long misses = 0;

    /**
     * Creates a new tile cache with the default maximum size.
     */
    public TileCache() {
        this(DEFAULT_MAX_SIZE);
    }

    /**
     * Creates a new tile cache with the specified maximum size.
     *
     * @param maxSize the maximum number of entries to keep in the cache
     * @throws IllegalArgumentException if maxSize is less than 1
     */
    public TileCache(int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize must be at least 1, got " + maxSize);
        }
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, byte[]> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * Returns the cached tile data for the given key, or null if not cached.
     *
     * @param layer the layer name
     * @param zoom  the zoom level
     * @param row   the row index
     * @param col   the column index
     * @return the cached tile data, or null if not in the cache
     */
    public synchronized @Nullable byte[] get(String layer, int zoom, int row, int col) {
        var key = new CacheKey(layer, zoom, row, col);
        var data = cache.get(key);
        if (data != null) {
            hits++;
        } else {
            misses++;
        }
        return data;
    }

    /**
     * Stores tile data in the cache.
     *
     * @param layer the layer name
     * @param zoom  the zoom level
     * @param row   the row index
     * @param col   the column index
     * @param data  the tile data to cache
     */
    public synchronized void put(String layer, int zoom, int row, int col, byte[] data) {
        cache.put(new CacheKey(layer, zoom, row, col), data);
    }

    /**
     * Returns the number of entries currently in the cache.
     *
     * @return the cache size
     */
    public synchronized int size() {
        return cache.size();
    }

    /**
     * Returns the number of cache hits since the cache was created.
     *
     * @return the hit count
     */
    public synchronized long getHits() {
        return hits;
    }

    /**
     * Returns the number of cache misses since the cache was created.
     *
     * @return the miss count
     */
    public synchronized long getMisses() {
        return misses;
    }

    /**
     * Clears all entries from the cache.
     */
    public synchronized void clear() {
        cache.clear();
    }

    /**
     * Returns the maximum number of entries this cache can hold.
     *
     * @return the maximum size
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Cache key identifying a tile by layer, zoom, row, and column.
     */
    record CacheKey(String layer, int zoom, int row, int col) {
    }
}

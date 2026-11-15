package volumet.project.cache;

import volumet.project.keys.VersionedKey;

/**
 * Cache whose writes are ordered by data version: a write only lands if the entry is absent or holds an
 * older version. A slow reader that loaded version 1 can therefore never overwrite version 3 written by an
 * update in the meantime, on this instance or any other.
 */
public interface VersionedCache {

    /** Data version stored for a cached "not found": lower than any real version, so any real value replaces it. */
    long NOT_FOUND_VERSION = -1L;

    /** Data version of a deletion marker: higher than any real version, so nothing replaces it until it expires. */
    long DELETED_VERSION = Long.MAX_VALUE;

    /**
     * @return the stored entry, or {@code null} if there is none. An entry whose {@code value} is null is a cached
     *         "not found" or deletion marker. {@link Entry#fresh()} is false once the entry is past the key space's
     *         TTL and inside its stale window.
     */
    <T> Entry<T> get(VersionedKey key, Class<T> type);

    /**
     * Stores {@code value} (null = "not found" / deleted) under {@code version}, but only if the key is absent or
     * holds a strictly lower version. Atomic across all instances. The entry lives for the key space's
     * {@link volumet.project.keys.KeySpace#storageTtl() storage TTL}.
     * <p>
     * An <b>equal</b> version keeps the stored data but renews its TTL, so the entry is fresh again: the caller
     * has just confirmed (from the DB) that this version is still current. This is how a revalidation that
     * finds nothing changed ends the stale phase.
     *
     * @return true if written, false if an equal (TTL renewed) or newer version was already stored
     */
    boolean putIfNewer(VersionedKey key, Object value, long version);

    /** Removes the entry outright. The next read reloads it; see {@link #putIfNewer} for ordered writes. */
    void evict(VersionedKey key);

    /**
     * A stored value with its data version.
     *
     * @param fresh false if past the TTL (inside the stale window)
     */
    record Entry<T>(long version, T value, boolean fresh) {

        /** A fresh entry. */
        public Entry(long version, T value) {
            this(version, value, true);
        }
    }
}

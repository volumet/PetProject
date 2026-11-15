package volumet.project.cache;

/**
 * How {@link volumet.project.components.CacheStampedeGuard#get} treats an entry that is past its fresh TTL
 * but still inside the key space's stale window ({@link volumet.project.keys.KeySpace#withStaleWindow}).
 * <pre>
 *   write ──── ttl (fresh) ────▶ ──── staleWindow (stale) ────▶ gone (Redis expiry)
 * </pre>
 * Fresh entries and misses behave the same under both policies.
 */
public enum CacheReadPolicy {
    /**
     * Never returns a stale entry: it is treated as a miss and the caller waits for the reload
     * (coalesced and locked like any miss). Use it where callers act on the value (e.g. a booking decision).
     */
    FRESH_ONLY,

    /**
     * Eventual consistency: a stale entry is returned at once and reloaded in the background (one reload per
     * key on this instance, and the distributed lock keeps it to one across instances). Callers never wait on
     * the DB while an entry is stale, at the cost of reading data up to {@code ttl + staleWindow} old.
     * In a key space without a stale window it behaves like {@link #FRESH_ONLY}.
     */
    STALE_WHILE_REVALIDATE
}

package volumet.project.keys;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A named family of cache keys: name, schema version of the stored type, entry TTL and optional stale window.
 * <p>
 * Two different versions are involved in caching; this is only the first:
 * <ul>
 *   <li><b>schemaVersion</b> (here, in code): the shape of the cached type. Bump it when the type changes
 *       (field added, renamed, removed or retyped) or the storage format changes; old and new pods then use
 *       separate entries (rooms::v1:42 vs rooms::v2:42) during a rolling deploy.</li>
 *   <li><b>data version</b> (per entry, at runtime): the entity's {@code @Version}, stored as cache-entry metadata next to
 *       the value ({@link volumet.project.cache.VersionedValue}), never inside the DTO. A write never replaces a
 *       newer data version with an older one.</li>
 * </ul>
 * Lifetime of an entry:
 * <pre>
 *   write ──── ttl (fresh) ────▶ ──── staleWindow (stale) ────▶ gone
 * </pre>
 * Redis keeps the entry for {@link #storageTtl()} = ttl + staleWindow. Whether a stale entry is served is the
 * reader's choice ({@link volumet.project.cache.CacheReadPolicy}).
 * <pre>
 * public static final KeySpace ROOMS = KeySpace.of("rooms", 2, Duration.ofMinutes(10))
 *         .withStaleWindow(Duration.ofMinutes(5));
 * guard.get(ROOMS.key(roomId), RoomResponseDto.class, STALE_WHILE_REVALIDATE, () -> repository.findVersionedById(roomId));
 * </pre>
 *
 * @param name          cache name: letters, digits, '.', '_' or '-' (no ':', which separates key segments)
 * @param schemaVersion version of the cached type's shape, starting at 1
 * @param ttl           how long an entry is fresh; {@code null} = no expiry (always fresh)
 * @param staleWindow   how long an entry stays readable as stale after {@code ttl}; zero = no stale phase.
 *                      Requires a ttl.
 */
public record KeySpace(String name, int schemaVersion, Duration ttl, Duration staleWindow) {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    public KeySpace {
        Objects.requireNonNull(name, "name");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid key space name '" + name + "': use letters, digits, '.', '_' or '-'");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("Key space schema version must be >= 1, was " + schemaVersion);
        }
        if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
            throw new IllegalArgumentException("Key space TTL must be positive (or null for no expiry), was " + ttl);
        }
        staleWindow = staleWindow == null ? Duration.ZERO : staleWindow;
        if (staleWindow.isNegative()) {
            throw new IllegalArgumentException("Key space stale window must not be negative, was " + staleWindow);
        }
        if (!staleWindow.isZero() && ttl == null) {
            throw new IllegalArgumentException("A stale window needs a TTL: entries without expiry are never stale");
        }
    }

    /** Key space without a stale window. */
    public KeySpace(String name, int schemaVersion, Duration ttl) {
        this(name, schemaVersion, ttl, Duration.ZERO);
    }

    /** Key space whose entries never expire. */
    public static KeySpace of(String name, int schemaVersion) {
        return new KeySpace(name, schemaVersion, null);
    }

    public static KeySpace of(String name, int schemaVersion, Duration ttl) {
        return new KeySpace(name, schemaVersion, Objects.requireNonNull(ttl, "ttl"));
    }

    /**
     * Same key space with a stale window after the TTL, for {@code STALE_WHILE_REVALIDATE} reads.
     * Changing it doesn't change the keys, so no schema version bump is needed.
     */
    public KeySpace withStaleWindow(Duration staleWindow) {
        return new KeySpace(name, schemaVersion, ttl, Objects.requireNonNull(staleWindow, "staleWindow"));
    }

    /** How long the store keeps an entry: ttl + staleWindow; {@code null} = no expiry. */
    public Duration storageTtl() {
        return ttl == null ? null : ttl.plus(staleWindow);
    }

    /** Key for one entry, e.g. {@code ROOMS.key(42)} or {@code PERSON_UUID.key(issuer, subject)}. */
    public VersionedKey key(Object part, Object... moreParts) {
        return VersionedKey.of(this, part, moreParts);
    }
}

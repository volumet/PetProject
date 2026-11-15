package volumet.project.playground.keys;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * W2 versioned keys, part 1: a named family of keys. YOUR IMPLEMENTATION GOES HERE.
 * <p>
 * Every cache / lock / coalescer key belongs to a key space, e.g.
 * {@code KeySpace.of("rooms", 2, Duration.ofMinutes(10))}, and is built only through it: {@code ROOMS.key(42)}.
 * <p>
 * The <b>schema version</b> describes the <i>shape</i> of what is stored under these keys (e.g. the JSON of
 * {@code RoomResponseDto}). Bump it when that shape changes: new keys ({@code rooms:v3:42}) are then disjoint
 * from the old ones ({@code rooms:v2:42}), so during a rolling deploy old and new pods never read each other's
 * JSON from Redis.
 * <p>
 * Rules (spec: {@code KeySpaceTest}):
 * <ul>
 *   <li>{@code name}: not null; letters, digits, {@code '.'}, {@code '_'} or {@code '-'}, starting with a letter
 *       or digit. In particular no {@code ':'}, the segment separator;</li>
 *   <li>{@code schemaVersion} &gt;= 1;</li>
 *   <li>{@code ttl}: {@code null} (no expiry) or strictly positive;</li>
 *   <li>violations: {@link IllegalArgumentException} ({@link NullPointerException} for a null name).</li>
 * </ul>
 */
public record KeySpace(String name, int schemaVersion, Duration ttl) {
    private static final Pattern KEY_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    public KeySpace {
        Objects.requireNonNull(name, "name");
        if (!KEY_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid key space name %s: letters, digits, '.', '_', '-' only".formatted(name));
        }

        if (schemaVersion < 1) {
            throw new IllegalArgumentException("Schema version must at least be 1");
        }

        if (ttl != null && !ttl.isPositive()) {
            throw new IllegalArgumentException("TTL must be positive");
        }
    }

    /** Key space whose entries never expire. */
    public static KeySpace of(String name, int schemaVersion) {
        return new KeySpace(name, schemaVersion, null);
    }

    /** Key space with a TTL; {@code ttl} must not be null here (use the 2-arg factory for "no expiry"). */
    public static KeySpace of(String name, int schemaVersion, Duration ttl) {
        Objects.requireNonNull(ttl, "ttl");
        return new KeySpace(name, schemaVersion, ttl);
    }

    /** Key for one entry: {@code ROOMS.key(42)}, {@code PERSON.key(issuer, subject)}. */
    public VersionedKey key(Object part, Object... moreParts) {
        return VersionedKey.of(this, part, moreParts);
    }
}

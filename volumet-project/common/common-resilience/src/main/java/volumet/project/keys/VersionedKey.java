package volumet.project.keys;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static volumet.project.constants.StringConstants.COLON_SEPARATOR;

/**
 * Typed key for the Redis cache, the Redis distributed lock and the request coalescer.
 * Create it through {@link KeySpace#key(Object, Object...)}.
 * <ul>
 *   <li>Parts are stored as strings, so {@code ROOMS.key(42)} equals {@code ROOMS.key(42L)}.</li>
 *   <li>':' and '%' inside a part are escaped, so parts can't collide
 *       (key("a:b") and key("a", "b") are different keys).</li>
 * </ul>
 * Renderings:
 * <ul>
 *   <li>{@link #cacheKey()}: inside the space's cache, e.g. {@code v1:42} (Redis: {@code rooms::v1:42})</li>
 *   <li>{@link #qualified()}: unique across spaces, e.g. {@code rooms:v1:42} (lock keys, logs, toString)</li>
 * </ul>
 */
public record VersionedKey(KeySpace space, List<String> parts) {
    // escaping rules specific to this key format (percent-encoding of the two special characters)
    private static final String PERCENT = "%";
    private static final String ESCAPED_PERCENT = "%25";
    private static final String ESCAPED_COLON = "%3A";
    // marks the schema version segment: v2
    private static final String SCHEMA_VERSION_PREFIX = "v";

    public VersionedKey {
        Objects.requireNonNull(space, "space");
        parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("A key needs at least one part");
        }
        for (String part : parts) {
            if (part.isBlank()) {
                throw new IllegalArgumentException("Key parts must not be blank: " + parts);
            }
        }
    }

    static VersionedKey of(KeySpace space, Object part, Object... moreParts) {
        List<String> parts = new ArrayList<>(1 + moreParts.length);
        parts.add(String.valueOf(Objects.requireNonNull(part, "key part")));
        for (Object more : moreParts) {
            parts.add(String.valueOf(Objects.requireNonNull(more, "key part")));
        }
        return new VersionedKey(space, parts);
    }

    /** Key within the space's cache: {@code v<schemaVersion>:<part>[:<part>...]}. */
    public String cacheKey() {
        return SCHEMA_VERSION_PREFIX + space.schemaVersion() + COLON_SEPARATOR
                + parts.stream().map(VersionedKey::escape).collect(Collectors.joining(COLON_SEPARATOR));
    }

    /** Key unique across all spaces: {@code <name>:v<schemaVersion>:<part>[:<part>...]}. */
    public String qualified() {
        return space.name() + COLON_SEPARATOR + cacheKey();
    }

    @Override
    public String toString() {
        return qualified();
    }

    private static String escape(String part) {
        return part.replace(PERCENT, ESCAPED_PERCENT).replace(COLON_SEPARATOR, ESCAPED_COLON);
    }
}

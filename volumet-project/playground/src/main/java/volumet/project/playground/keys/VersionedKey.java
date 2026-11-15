package volumet.project.playground.keys;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * W2 versioned keys, part 2: one typed key. YOUR IMPLEMENTATION GOES HERE.
 * <p>
 * Built only through {@link KeySpace#key(Object, Object...)}. Two renderings:
 * <ul>
 *   <li>{@link #cacheKey()}: inside the space's cache: {@code v<schemaVersion>:<part>[:<part>...]}, e.g. {@code v2:42}
 *       (Spring Cache puts the cache name in front: {@code rooms::v2:42});</li>
 *   <li>{@link #qualified()}: unique across all spaces: {@code <name>:v<schemaVersion>:<part>...}, e.g.
 *       {@code rooms:v2:42}. Used for lock keys, coalescer keys and logs; {@link #toString()} returns it.</li>
 * </ul>
 * Rules (spec: {@code VersionedKeyTest}):
 * <ul>
 *   <li>{@code space} and {@code parts} not null; at least one part; no null or blank part;</li>
 *   <li>parts are stored as strings ({@code String.valueOf}), so {@code key(42)} equals {@code key(42L)};</li>
 *   <li>the record must be immutable: later changes to the caller's list don't affect it;</li>
 *   <li><b>no collisions</b>: {@code ':'} inside a part is escaped as {@code %3A} and {@code '%'} as {@code %25},
 *       so {@code key("a:b")} and {@code key("a", "b")} render differently, and so do {@code key(":")} and
 *       {@code key("%3A")}. Think about the order of the two replacements.</li>
 * </ul>
 * Keep the separator and escape strings as named private constants, not inline literals.
 */
public record VersionedKey(KeySpace space, List<String> parts) {
    private static final String VERSION_PREFIX = "v";
    private static final String COLON_SEPARATOR = ":";
    private static final String PERCENTAGE = "%";
    private static final String ESCAPE_PERCENTAGE = "%25";
    private static final String ESCAPE_COLON = "%3A";

    public VersionedKey {
        parts = List.copyOf(parts);

        if (parts.isEmpty()) {
            throw new IllegalArgumentException("Parts must not be empty");
        }

        boolean anyBlank = parts.stream().anyMatch(String::isBlank);
        if (anyBlank) {
            throw new IllegalArgumentException("Part must not be blank");
        }
    }

    /**
     * Used by {@link KeySpace#key}: converts each part with {@code String.valueOf}; a null part is rejected.
     */
    static VersionedKey of(KeySpace space, Object part, Object... moreParts) {
        return new VersionedKey(space,
                Stream.concat(Stream.of(part),
                                Arrays.stream(moreParts))
                        .map(p -> Objects.requireNonNull(p, "key part"))
                        .map(String::valueOf)
                        .toList());
    }

    public String cacheKey() {
        String combinedParts = parts.stream().map(VersionedKey::escape).collect(Collectors.joining(COLON_SEPARATOR));
        return VERSION_PREFIX + space().schemaVersion() + COLON_SEPARATOR + combinedParts;
    }

    public String qualified() {
        return space.name() + COLON_SEPARATOR + cacheKey();
    }

    private static String escape(String part) {
        return part.replace(PERCENTAGE, ESCAPE_PERCENTAGE).replace(COLON_SEPARATOR, ESCAPE_COLON);
    }

    @Override
    public String toString() {
        return qualified();
    }
}

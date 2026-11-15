package volumet.project.cache;

import java.util.Objects;

/**
 * Cache-layer envelope: a value plus its data version (normally the entity's JPA {@code @Version}), kept
 * separate from the value so API DTOs stay free of caching concerns.
 * <p>
 * Loaders return it (read the version in the same query as the DTO), and the cache stores the version as entry
 * metadata next to the value: Redis hash {@code rooms::v2:42 = {v: 3, d: <dto json>}}. Callers of the cache
 * only ever get the plain value back.
 *
 * @param value   the value to cache (never null; a loader returns {@code null} instead of a VersionedValue
 *                when nothing was found)
 * @param version its data version; writes with a lower or equal version than the stored one are ignored
 */
public record VersionedValue<T>(T value, long version) {

    public VersionedValue {
        Objects.requireNonNull(value, "value (return null from the loader for 'not found')");
    }

    public static <T> VersionedValue<T> of(T value, long version) {
        return new VersionedValue<>(value, version);
    }
}

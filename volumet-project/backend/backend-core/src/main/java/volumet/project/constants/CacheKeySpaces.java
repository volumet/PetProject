package volumet.project.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import volumet.project.keys.KeySpace;

import java.time.Duration;

/**
 * Redis cache key spaces of this app: name, schema version of the cached type, TTL.
 * Bump the schema version whenever the cached type changes shape (field added, renamed, removed or retyped):
 * old and new pods then use separate entries during a rolling deploy, and the old ones expire by TTL.
 * (The per-entry data version is the entity's @Version, stored next to the DTO as cache-entry metadata.)
 * Add {@code .withStaleWindow(Duration)} to a space read with CacheReadPolicy.STALE_WHILE_REVALIDATE.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CacheKeySpaces {
    /**
     * RoomResponseDto by room id. Rooms change rarely and are read heavily at semester start.
     * v2: version-ordered hash entries (v1 were plain Spring Cache values).
     */
    public static final KeySpace ROOMS = KeySpace.of("rooms", 2, Duration.ofMinutes(10));
}

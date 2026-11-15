package volumet.project.playground.keys;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersionedKeyTest {

    private static final KeySpace ROOMS = KeySpace.of("rooms", 2, Duration.ofMinutes(10));
    private static final KeySpace PERSONS = KeySpace.of("persons", 1);

    // ---------------------------------------------------------------- rendering

    @Test
    @DisplayName("cacheKey() = v<schema>:<parts>, qualified() = <name>:v<schema>:<parts>, toString() = qualified()")
    void rendering() {
        VersionedKey key = ROOMS.key(42);

        assertThat(key.cacheKey()).isEqualTo("v2:42");
        assertThat(key.qualified()).isEqualTo("rooms:v2:42");
        assertThat(key).hasToString("rooms:v2:42");
    }

    @Test
    @DisplayName("several parts are joined with ':'")
    void multipleParts() {
        VersionedKey key = PERSONS.key("keycloak", "sub-123");

        assertThat(key.parts()).containsExactly("keycloak", "sub-123");
        assertThat(key.cacheKey()).isEqualTo("v1:keycloak:sub-123");
        assertThat(key.qualified()).isEqualTo("persons:v1:keycloak:sub-123");
    }

    @Test
    @DisplayName("parts are converted with String.valueOf")
    void partsAsStrings() {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        assertThat(ROOMS.key(id).cacheKey()).isEqualTo("v2:123e4567-e89b-12d3-a456-426614174000");
        assertThat(ROOMS.key(true, 7L).cacheKey()).isEqualTo("v2:true:7");
    }

    // ---------------------------------------------------------------- equality

    @Test
    @DisplayName("ROOMS.key(42) equals ROOMS.key(42L): equal keys work as map keys")
    void equalityAcrossNumberTypes() {
        assertThat(ROOMS.key(42)).isEqualTo(ROOMS.key(42L)).hasSameHashCodeAs(ROOMS.key(42L));

        Map<VersionedKey, String> map = new HashMap<>();
        map.put(ROOMS.key(42), "room 42");
        assertThat(map).containsEntry(ROOMS.key(42L), "room 42");
    }

    @Test
    @DisplayName("the same id in two key spaces gives two different keys")
    void spacesDontCollide() {
        assertThat(ROOMS.key(42)).isNotEqualTo(PERSONS.key(42));
        assertThat(ROOMS.key(42).qualified()).isNotEqualTo(PERSONS.key(42).qualified());
    }

    @Test
    @DisplayName("bumping the schema version gives disjoint keys")
    void schemaBump() {
        KeySpace roomsV3 = KeySpace.of("rooms", 3, Duration.ofMinutes(10));

        assertThat(roomsV3.key(42)).isNotEqualTo(ROOMS.key(42));
        assertThat(roomsV3.key(42).cacheKey()).isEqualTo("v3:42");
        assertThat(roomsV3.key(42).qualified()).isNotEqualTo(ROOMS.key(42).qualified());
    }

    // ---------------------------------------------------------------- escaping

    @Test
    @DisplayName("':' inside a part is escaped, so key(\"a:b\") never collides with key(\"a\", \"b\")")
    void colonIsEscaped() {
        VersionedKey joined = ROOMS.key("a:b");
        VersionedKey split = ROOMS.key("a", "b");

        assertThat(joined.cacheKey()).isEqualTo("v2:a%3Ab");
        assertThat(split.cacheKey()).isEqualTo("v2:a:b");
        assertThat(joined).isNotEqualTo(split);
        assertThat(joined.parts()).as("parts keep the raw value").containsExactly("a:b");
    }

    @Test
    @DisplayName("'%' is escaped too, so a part that looks escaped can't collide with a real ':'")
    void percentIsEscaped() {
        assertThat(ROOMS.key(":").cacheKey()).isEqualTo("v2:%3A");
        assertThat(ROOMS.key("%3A").cacheKey()).isEqualTo("v2:%253A");
        assertThat(ROOMS.key("100%").cacheKey()).isEqualTo("v2:100%25");
    }

    // ---------------------------------------------------------------- validation and immutability

    @Test
    @DisplayName("null space, null/empty parts, null or blank part are rejected")
    void validation() {
        assertThatThrownBy(() -> new VersionedKey(null, List.of("42"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VersionedKey(ROOMS, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VersionedKey(ROOMS, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VersionedKey(ROOMS, List.of("42", " "))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ROOMS.key(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ROOMS.key("a", (Object) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ROOMS.key("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the key is immutable: changing the caller's list afterwards changes nothing")
    void immutable() {
        List<String> parts = new ArrayList<>(List.of("42"));
        VersionedKey key = new VersionedKey(ROOMS, parts);

        parts.set(0, "43");
        parts.add("44");

        assertThat(key.cacheKey()).isEqualTo("v2:42");
        assertThatThrownBy(() -> key.parts().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }
}

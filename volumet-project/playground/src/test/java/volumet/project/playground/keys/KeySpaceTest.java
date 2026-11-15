package volumet.project.playground.keys;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class KeySpaceTest {

    private static final Duration TEN_MIN = Duration.ofMinutes(10);

    @Test
    @DisplayName("valid names: letters, digits, '.', '_', '-'")
    void validNames() {
        for (String name : List.of("rooms", "Rooms2", "person.uuid", "room_read", "a-b", "9lives")) {
            assertThat(KeySpace.of(name, 1).name()).isEqualTo(name);
        }
    }

    @Test
    @DisplayName("invalid names are rejected, especially ':' which would break key segments")
    void invalidNames() {
        assertThatThrownBy(() -> KeySpace.of(null, 1)).isInstanceOf(NullPointerException.class);
        for (String name : List.of("", " ", "rooms:v2", "my rooms", "-rooms", ".rooms", "räume", "rooms/1")) {
            assertThat(catchThrowable(() -> KeySpace.of(name, 1)))
                    .as("name '%s'", name)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("schema version starts at 1")
    void schemaVersion() {
        assertThatThrownBy(() -> KeySpace.of("rooms", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KeySpace.of("rooms", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(KeySpace.of("rooms", 1).schemaVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("ttl: null = no expiry; otherwise strictly positive")
    void ttl() {
        assertThat(KeySpace.of("rooms", 1).ttl()).isNull();
        assertThat(KeySpace.of("rooms", 1, TEN_MIN).ttl()).isEqualTo(TEN_MIN);

        assertThatThrownBy(() -> KeySpace.of("rooms", 1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KeySpace.of("rooms", 1, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KeySpace.of("rooms", 1, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the canonical constructor is validated too, not only the factories")
    void constructorIsValidated() {
        assertThatThrownBy(() -> new KeySpace("rooms:x", 1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KeySpace("rooms", 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KeySpace("rooms", 1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new KeySpace("rooms", 1, null)).isEqualTo(KeySpace.of("rooms", 1));
    }

    @Test
    @DisplayName("key() builds a VersionedKey in this space")
    void key() {
        KeySpace rooms = KeySpace.of("rooms", 2, TEN_MIN);

        VersionedKey key = rooms.key(42);
        assertThat(key.space()).isEqualTo(rooms);
        assertThat(key.parts()).containsExactly("42");
    }
}

package volumet.project.keys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class VersionedKeyTest {
    private static final KeySpace ROOMS_V1 = KeySpace.of("rooms", 1);
    private static final KeySpace ROOMS_V2 = KeySpace.of("rooms", 2);

    @Test
    void rendersCacheKeyAndQualifiedKey() {
        VersionedKey key = ROOMS_V1.key(42);

        assertThat(key.cacheKey()).isEqualTo("v1:42");
        assertThat(key.qualified()).isEqualTo("rooms:v1:42");
        assertThat(key).hasToString("rooms:v1:42");
    }

    @Test
    void joinsMultipleParts() {
        assertThat(KeySpace.of("person-uuid", 3).key("issuer", "subject").qualified())
                .isEqualTo("person-uuid:v3:issuer:subject");
    }

    @Test
    void equalWhenSpaceAndPartsMatchRegardlessOfNumericType() {
        assertThat(ROOMS_V1.key(42)).isEqualTo(ROOMS_V1.key(42L)).hasSameHashCodeAs(ROOMS_V1.key(42L));
    }

    @Test
    void differentVersionsAreDifferentKeys() {
        assertThat(ROOMS_V1.key(42)).isNotEqualTo(ROOMS_V2.key(42));
        assertThat(ROOMS_V2.key(42).cacheKey()).isEqualTo("v2:42");
    }

    @Test
    void differentSpacesAreDifferentKeys() {
        assertThat(ROOMS_V1.key(42)).isNotEqualTo(KeySpace.of("students", 1).key(42));
    }

    @Test
    void separatorInsidePartIsEscapedSoPartsCannotCollide() {
        VersionedKey onePart = ROOMS_V1.key("a:b");
        VersionedKey twoParts = ROOMS_V1.key("a", "b");

        assertThat(onePart).isNotEqualTo(twoParts);
        assertThat(onePart.cacheKey()).isEqualTo("v1:a%3Ab");
        assertThat(twoParts.cacheKey()).isEqualTo("v1:a:b");
    }

    @Test
    void escapeCharacterItselfIsEscaped() {
        // without escaping '%', key("a%3Ab") would render the same as key("a:b")
        assertThat(ROOMS_V1.key("a%3Ab").cacheKey()).isEqualTo("v1:a%253Ab");
        assertThat(ROOMS_V1.key("a%3Ab").cacheKey()).isNotEqualTo(ROOMS_V1.key("a:b").cacheKey());
    }

    @Test
    void partsAreImmutable() {
        VersionedKey key = ROOMS_V1.key(1, 2);

        assertThat(key.parts()).containsExactly("1", "2");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> key.parts().add("3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullPart() {
        assertThatNullPointerException().isThrownBy(() -> ROOMS_V1.key(null));
        assertThatNullPointerException().isThrownBy(() -> ROOMS_V1.key(1, (Object) null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsBlankPart(String part) {
        assertThatIllegalArgumentException().isThrownBy(() -> ROOMS_V1.key(part));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ro:oms", "rooms::x", "-rooms", "room s"})
    void rejectsInvalidSpaceName(String name) {
        assertThatIllegalArgumentException().isThrownBy(() -> KeySpace.of(name, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"rooms", "person-uuid", "orders.v2", "a_b", "Rooms1"})
    void acceptsValidSpaceName(String name) {
        assertThat(KeySpace.of(name, 1).name()).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsSchemaVersionBelowOne(int version) {
        assertThatIllegalArgumentException().isThrownBy(() -> KeySpace.of("rooms", version));
    }

    @Test
    void ttlIsOptional() {
        assertThat(KeySpace.of("rooms", 1).ttl()).isNull();
        assertThat(KeySpace.of("rooms", 1, java.time.Duration.ofMinutes(10)).ttl()).isEqualTo(java.time.Duration.ofMinutes(10));
    }

    @Test
    void rejectsNonPositiveTtl() {
        assertThatIllegalArgumentException().isThrownBy(() -> KeySpace.of("rooms", 1, java.time.Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> KeySpace.of("rooms", 1, java.time.Duration.ofSeconds(-1)));
        assertThatNullPointerException().isThrownBy(() -> KeySpace.of("rooms", 1, null));
    }

    @Test
    void ttlIsNotPartOfTheKey() {
        assertThat(KeySpace.of("rooms", 1, java.time.Duration.ofMinutes(10)).key(42).cacheKey())
                .isEqualTo(KeySpace.of("rooms", 1).key(42).cacheKey());
    }

    @Test
    void staleWindowDefaultsToZeroAndExtendsTheStorageTtl() {
        KeySpace plain = KeySpace.of("rooms", 1, java.time.Duration.ofMinutes(10));
        KeySpace swr = plain.withStaleWindow(java.time.Duration.ofMinutes(5));

        assertThat(plain.staleWindow()).isZero();
        assertThat(plain.storageTtl()).isEqualTo(java.time.Duration.ofMinutes(10));
        assertThat(swr.storageTtl()).isEqualTo(java.time.Duration.ofMinutes(15));
        assertThat(KeySpace.of("rooms", 1).storageTtl()).isNull();
        // same keys: adding a stale window needs no schema bump
        assertThat(swr.key(42).cacheKey()).isEqualTo(plain.key(42).cacheKey());
    }

    @Test
    void rejectsInvalidStaleWindow() {
        assertThatIllegalArgumentException().isThrownBy(() -> KeySpace.of("rooms", 1, java.time.Duration.ofMinutes(1))
                .withStaleWindow(java.time.Duration.ofSeconds(-1)));
        // entries without expiry are never stale
        assertThatIllegalArgumentException().isThrownBy(() -> KeySpace.of("rooms", 1).withStaleWindow(java.time.Duration.ofMinutes(1)));
    }
}

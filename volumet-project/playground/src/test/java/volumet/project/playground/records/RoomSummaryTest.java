package volumet.project.playground.records;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomSummaryTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("accessors are named after the components (no get- prefix)")
    void accessors() {
        RoomSummary room = new RoomSummary(1, "Lab", 20);

        assertThat(room.id()).isEqualTo(1);
        assertThat(room.name()).isEqualTo("Lab");
        assertThat(room.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("equals/hashCode compare all components, so equal records work as set/map keys")
    void valueEquality() {
        RoomSummary a = new RoomSummary(1, "Lab", 20);
        RoomSummary b = new RoomSummary(1, "Lab", 20);

        assertThat(a).isEqualTo(b).isNotSameAs(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(new RoomSummary(1, "Lab", 21));

        Set<RoomSummary> set = new HashSet<>(Set.of(a));
        assertThat(set).contains(b);
    }

    @Test
    @DisplayName("toString lists the components")
    void readableToString() {
        assertThat(new RoomSummary(1, "Lab", 20)).hasToString("RoomSummary[id=1, name=Lab, size=20]");
    }

    @Test
    @DisplayName("the name is trimmed in the compact constructor")
    void nameIsTrimmed() {
        assertThat(new RoomSummary(1, "  Lab  ", 20).name()).isEqualTo("Lab");
        assertThat(new RoomSummary(1, "  Lab  ", 20)).isEqualTo(new RoomSummary(1, "Lab", 20));
    }

    @Test
    @DisplayName("invalid components are rejected")
    void validation() {
        assertThatThrownBy(() -> new RoomSummary(0, "Lab", 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoomSummary(-1, "Lab", 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoomSummary(1, null, 20)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RoomSummary(1, "   ", 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoomSummary(1, "Lab", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the convenience constructor delegates to the canonical one (size 1, still validated)")
    void convenienceConstructor() {
        assertThat(new RoomSummary(1, " Lab ")).isEqualTo(new RoomSummary(1, "Lab", 1));
        assertThatThrownBy(() -> new RoomSummary(1, " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("withSize returns a new record and leaves the original unchanged")
    void wither() {
        RoomSummary original = new RoomSummary(1, "Lab", 20);
        RoomSummary bigger = original.withSize(30);

        assertThat(bigger).isEqualTo(new RoomSummary(1, "Lab", 30));
        assertThat(original.size()).isEqualTo(20);
        assertThatThrownBy(() -> original.withSize(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Jackson serializes a record by its components and deserializes through the canonical constructor")
    void jsonRoundTrip() throws Exception {
        RoomSummary room = new RoomSummary(1, "Lab", 20);

        String text = json.writeValueAsString(room);
        assertThat(text).isEqualTo("{\"id\":1,\"name\":\"Lab\",\"size\":20}");
        assertThat(json.readValue(text, RoomSummary.class)).isEqualTo(room);
    }

    @Test
    @DisplayName("deserialization runs the compact constructor too: invalid JSON is rejected, valid JSON is normalized")
    void jsonGoesThroughValidation() throws Exception {
        // text blocks (Java 15): multi-line strings, no \" escaping, indentation stripped up to the closing """
        String untrimmed = """
                {
                  "id": 1,
                  "name": "  Lab ",
                  "size": 20
                }
                """;
        String blankName = """
                { "id": 1, "name": " ", "size": 20 }
                """;

        assertThat(json.readValue(untrimmed, RoomSummary.class).name()).isEqualTo("Lab");
        assertThatThrownBy(() -> json.readValue(blankName, RoomSummary.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}

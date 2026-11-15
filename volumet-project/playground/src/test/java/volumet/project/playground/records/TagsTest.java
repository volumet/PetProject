package volumet.project.playground.records;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TagsTest {

    @Test
    @DisplayName("changing the caller's list afterwards does not change the record (defensive copy)")
    void copiesTheInput() {
        List<String> source = new ArrayList<>(List.of("quiet", "window"));
        Tags tags = new Tags(source);

        source.add("noisy");
        source.set(0, "changed");

        assertThat(tags.values()).containsExactly("quiet", "window");
    }

    @Test
    @DisplayName("the list returned by values() cannot be modified")
    void exposesAnUnmodifiableList() {
        Tags tags = new Tags(List.of("quiet"));

        assertThatThrownBy(() -> tags.values().add("noisy")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> tags.values().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("null list and null elements are rejected")
    void rejectsNulls() {
        assertThatThrownBy(() -> new Tags(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Tags(Arrays.asList("quiet", null))).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("equality is by content and order, whatever list type was passed in")
    void equality() {
        assertThat(new Tags(new ArrayList<>(List.of("a", "b")))).isEqualTo(new Tags(List.of("a", "b")));
        assertThat(new Tags(List.of("a", "b"))).isNotEqualTo(new Tags(List.of("b", "a")));
    }

    @Test
    @DisplayName("none() is empty; contains() matches exactly")
    void noneAndContains() {
        assertThat(Tags.none().values()).isEmpty();
        assertThat(Tags.none()).isEqualTo(new Tags(List.of()));

        Tags tags = new Tags(List.of("quiet"));
        assertThat(tags.contains("quiet")).isTrue();
        assertThat(tags.contains("Quiet")).isFalse();
    }

    @Test
    @DisplayName("with() returns a new Tags and leaves the original unchanged")
    void withAppends() {
        Tags original = new Tags(List.of("quiet"));
        Tags more = original.with("window");

        assertThat(more.values()).containsExactly("quiet", "window");
        assertThat(original.values()).containsExactly("quiet");
        assertThatThrownBy(() -> original.with(null)).isInstanceOf(NullPointerException.class);
    }
}

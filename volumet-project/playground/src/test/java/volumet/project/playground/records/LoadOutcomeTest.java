package volumet.project.playground.records;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoadOutcomeTest {

    @Test
    @DisplayName("a hit needs a value and a failure needs a cause")
    void validation() {
        assertThatThrownBy(() -> new LoadOutcome.Hit<>(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LoadOutcome.Failed<>(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("records give the cases value equality for free")
    void equality() {
        assertThat(new LoadOutcome.Hit<>("room")).isEqualTo(new LoadOutcome.Hit<>("room"));
        assertThat(new LoadOutcome.Miss<>()).isEqualTo(new LoadOutcome.Miss<>());
    }

    @Test
    @DisplayName("describe() covers every case")
    void describe() {
        assertThat(LoadOutcome.describe(new LoadOutcome.Hit<>("room-1"))).isEqualTo("hit: room-1");
        assertThat(LoadOutcome.describe(new LoadOutcome.Miss<>())).isEqualTo("miss");
        assertThat(LoadOutcome.describe(new LoadOutcome.Failed<>(new IllegalStateException("db down"))))
                .isEqualTo("failed: db down");
    }

    @Test
    @DisplayName("orElse(): value on hit, fallback on miss")
    void orElseHitAndMiss() {
        LoadOutcome<String> hit = new LoadOutcome.Hit<>("room-1");
        LoadOutcome<String> miss = new LoadOutcome.Miss<>();

        assertThat(hit.orElse("fallback")).isEqualTo("room-1");
        assertThat(miss.orElse("fallback")).isEqualTo("fallback");
        assertThat(miss.orElse(null)).isNull();
    }

    @Test
    @DisplayName("orElse() on a failure rethrows a runtime cause as is and wraps a checked one")
    void orElseFailure() {
        IllegalStateException runtime = new IllegalStateException("db down");
        IOException checked = new IOException("socket closed");

        assertThatThrownBy(() -> new LoadOutcome.Failed<String>(runtime).orElse("x")).isSameAs(runtime);
        assertThatThrownBy(() -> new LoadOutcome.Failed<String>(checked).orElse("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(checked);
    }
}

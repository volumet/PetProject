package volumet.project.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationUtilsTest {
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void prefersCorrelationId() {
        MDC.put("correlationId", "corr");
        MDC.put("traceId", "trace");

        assertThat(CorrelationUtils.getCorrelationId()).isEqualTo("corr");
    }

    @Test
    void fallsBackToTraceId() {
        MDC.put("traceId", "trace");

        assertThat(CorrelationUtils.getCorrelationId()).isEqualTo("trace");
    }

    @Test
    void returnsNullWhenNeitherIsSet() {
        assertThat(CorrelationUtils.getCorrelationId()).isNull();
    }
}

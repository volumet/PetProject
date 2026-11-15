package volumet.project.tracing;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.observation.web.servlet.WebMvcObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import volumet.project.utils.CorrelationUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots a real embedded server with only the web and tracing auto-configuration, so it needs no database or broker.
 * Tracing is off by default in Spring Boot tests; {@code @AutoConfigureObservability} turns it back on.
 */
@SpringBootTest(
        classes = TracingIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.tracing.sampling.probability=1.0"
)
@AutoConfigureObservability(metrics = false)
class TracingIntegrationTest {
    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void putsTraceIdInMdcAndExposesItThroughCorrelationUtils() {
        Map<?, ?> body = rest.getForObject("/probe", Map.class);

        assertThat((String) body.get("traceId")).matches("[0-9a-f]{32}");
        assertThat((String) body.get("spanId")).matches("[0-9a-f]{16}");
        assertThat(body.get("correlationId")).isEqualTo(body.get("traceId"));
    }

    @Test
    void generatesNewTraceIdPerRequest() {
        Object first = rest.getForObject("/probe", Map.class).get("traceId");
        Object second = rest.getForObject("/probe", Map.class).get("traceId");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void continuesIncomingW3cTrace() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("traceparent", "00-" + TRACE_ID + "-b7ad6b7169203331-01");

        Map<?, ?> body = rest.exchange("/probe", HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();

        assertThat(body.get("traceId")).isEqualTo(TRACE_ID);
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({
            ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            MetricsAutoConfiguration.class,
            SimpleMetricsExportAutoConfiguration.class,
            CompositeMeterRegistryAutoConfiguration.class,
            ObservationAutoConfiguration.class,
            WebMvcObservationAutoConfiguration.class,
            OpenTelemetryAutoConfiguration.class,
            OpenTelemetryTracingAutoConfiguration.class,
            MicrometerTracingAutoConfiguration.class
    })
    @Import(ProbeController.class)
    static class TestApp {
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe")
        Map<String, String> probe() {
            return Map.of(
                    "traceId", String.valueOf(MDC.get("traceId")),
                    "spanId", String.valueOf(MDC.get("spanId")),
                    "correlationId", String.valueOf(CorrelationUtils.getCorrelationId())
            );
        }
    }
}

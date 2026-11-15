package volumet.project.filters;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static volumet.project.constants.CorrelationIdConstant.CORRELATION_ID_HEADER;
import static volumet.project.constants.CorrelationIdConstant.MDC_KEY;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc-123", "order_42.retry", "6f1c2c1e-7f0a-4c55-9a55-0a1b2c3d4e5f"})
    void keepsValidClientId(String id) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(requestWith(id), response, captureMdc(seen));

        assertThat(seen).hasValue(id);
        assertThat(response.getHeader(CORRELATION_ID_HEADER)).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "has space", "line\nbreak", "<script>", "${jndi:ldap://x}"})
    void replacesInvalidClientId(String id) throws Exception {
        assertGeneratesUuid(requestWith(id));
    }

    @Test
    void replacesOverlongClientId() throws Exception {
        assertGeneratesUuid(requestWith("a".repeat(65)));
    }

    @Test
    void generatesIdWhenHeaderMissing() throws Exception {
        assertGeneratesUuid(new MockHttpServletRequest());
    }

    @Test
    void removesOnlyItsOwnMdcKey() throws Exception {
        MDC.put("traceId", "keep-me");

        filter.doFilter(requestWith("abc-123"), new MockHttpServletResponse(), (req, res) -> { });

        assertThat(MDC.get(MDC_KEY)).isNull();
        assertThat(MDC.get("traceId")).isEqualTo("keep-me");
    }

    private void assertGeneratesUuid(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, captureMdc(seen));

        assertThatCode(() -> UUID.fromString(seen.get())).doesNotThrowAnyException();
        assertThat(response.getHeader(CORRELATION_ID_HEADER)).isEqualTo(seen.get());
    }

    private static FilterChain captureMdc(AtomicReference<String> seen) {
        return (req, res) -> seen.set(MDC.get(MDC_KEY));
    }

    private static MockHttpServletRequest requestWith(String id) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CORRELATION_ID_HEADER, id);
        return request;
    }
}

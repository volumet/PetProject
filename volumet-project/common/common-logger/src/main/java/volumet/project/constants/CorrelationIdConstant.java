package volumet.project.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CorrelationIdConstant {
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    /** MDC key Micrometer Tracing writes the current trace id to. */
    public static final String TRACE_ID_MDC_KEY = "traceId";
}

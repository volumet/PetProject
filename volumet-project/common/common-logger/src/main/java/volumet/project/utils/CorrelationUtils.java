package volumet.project.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;
import volumet.project.constants.CorrelationIdConstant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CorrelationUtils {
    /**
     * Returns the id set by CorrelationIdFilter, or the Micrometer Tracing trace id when that filter is disabled.
     */
    public static String getCorrelationId() {
        String correlationId = MDC.get(CorrelationIdConstant.MDC_KEY);
        return correlationId != null ? correlationId : MDC.get(CorrelationIdConstant.TRACE_ID_MDC_KEY);
    }
}

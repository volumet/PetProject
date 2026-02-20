package volumet.project.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;
import volumet.project.constants.CorrelationIdConstant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CorrelationUtils {
    public static String getCorrelationId() {
        return MDC.get(CorrelationIdConstant.MDC_KEY);
    }
}

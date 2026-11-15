package volumet.project.configs.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Validated at startup: an invalid override (yml, env var, k8s ConfigMap) fails the boot instead of
 * misbehaving under load.
 */
@ConfigurationProperties(prefix = "volumet.executor.request-coalescer")
@Validated
@Getter
@Setter
public class RequestCoalescerExecutorProperties {
    /**
     * Max loads running at the same time; further submissions wait until one finishes.
     * Virtual threads are cheap, so this limit exists to protect downstream resources (DB pool, remote APIs).
     * -1 means unlimited; 0 is not allowed (it would block every load).
     */
    @Min(-1)
    private int concurrencyLimit = 200;

    /** How long shutdown waits for running loads to finish. */
    @NotNull
    private Duration awaitTermination = Duration.ofSeconds(30);

    @NotBlank
    private String threadNamePrefix = "request-coalescer-";

    /** How long a caller waits for a coalesced result when no explicit timeout is given. */
    @NotNull
    @DurationMin(millis = 1)
    private Duration defaultTimeout = Duration.ofSeconds(10);

    @AssertTrue(message = "concurrencyLimit must be -1 (unlimited) or greater than 0")
    boolean isConcurrencyLimitValid() {
        return concurrencyLimit != 0;
    }
}

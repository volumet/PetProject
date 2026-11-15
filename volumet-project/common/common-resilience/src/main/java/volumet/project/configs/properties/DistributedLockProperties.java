package volumet.project.configs.properties;

import jakarta.validation.constraints.AssertTrue;
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
@ConfigurationProperties(prefix = "volumet.lock")
@Validated
@Getter
@Setter
public class DistributedLockProperties {
    /** Prefix of every lock key in Redis. */
    @NotBlank
    private String registryKey = "volumet-lock";

    /**
     * Lease time: a lock not released within this time expires in Redis (e.g. the holder crashed).
     * Must be longer than the slowest protected load.
     */
    @NotNull
    @DurationMin(millis = 1)
    private Duration expireAfter = Duration.ofSeconds(30);

    /** How long a caller waits to acquire a lock before giving up and loading anyway. */
    @NotNull
    @DurationMin(millis = 0)
    private Duration waitTime = Duration.ofSeconds(3);

    @AssertTrue(message = "waitTime must be shorter than expireAfter")
    boolean isWaitShorterThanLease() {
        return waitTime == null || expireAfter == null || waitTime.compareTo(expireAfter) < 0;
    }
}

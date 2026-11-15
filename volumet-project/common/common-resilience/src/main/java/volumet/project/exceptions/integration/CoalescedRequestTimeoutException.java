package volumet.project.exceptions.integration;

import lombok.Getter;
import volumet.project.keys.VersionedKey;

import java.time.Duration;

/**
 * A coalesced (shared) load did not complete within the caller's timeout.
 * Owned by common-resilience (thrown by its RequestCoalescer). Each app maps it to an HTTP response
 * in its exception handler, e.g. 504 GATEWAY_TIMEOUT in GeneralExceptionHandler.
 */
@Getter
public class CoalescedRequestTimeoutException extends RuntimeException {
    private final transient VersionedKey key;
    private final Duration timeout;

    public CoalescedRequestTimeoutException(VersionedKey key, Duration timeout) {
        super("Coalesced request " + key + " did not complete within " + timeout);
        this.key = key;
        this.timeout = timeout;
    }
}

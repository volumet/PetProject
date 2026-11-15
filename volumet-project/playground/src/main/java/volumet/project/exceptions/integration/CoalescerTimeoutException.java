package volumet.project.exceptions.integration;

import java.time.Duration;

/**
 * Thrown by the playground {@code RequestCoalescer.execute} when the caller's wait for the shared load exceeds
 * its timeout. Only this caller gives up; the shared load keeps running for everyone else.
 */
public class CoalescerTimeoutException extends RuntimeException {

    public CoalescerTimeoutException(String key, Duration timeout) {
        super("Timed out after " + timeout.toMillis() + " ms waiting for coalesced request '" + key + "'");
    }
}

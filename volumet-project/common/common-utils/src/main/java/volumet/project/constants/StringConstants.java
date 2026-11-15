package volumet.project.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Shared string constants. Name separators by the character they are, not by their use
 * ({@code COLON_SEPARATOR}, not {@code KEY_SEPARATOR}), so one constant serves every use.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StringConstants {
    public static final String EMPTY = "";

    /** ":" e.g. between the segments of a cache key: {@code rooms:v2:42}. */
    public static final String COLON_SEPARATOR = ":";

    /** "::" between a cache name and its key, as Spring's RedisCache does: {@code rooms::v2:42}. */
    public static final String DOUBLE_COLON_SEPARATOR = "::";
}

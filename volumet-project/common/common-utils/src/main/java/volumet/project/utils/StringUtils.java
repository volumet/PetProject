package volumet.project.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Locale;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StringUtils {
    /**
     * To prevent cases like: <br/>
     * ExternalIdentifiers is converted into EXTERNALIDENTIFIERS <br/>
     */
    public static String toUpperSnakeCase(String input) {
        return input
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toUpperCase(Locale.ROOT);
    }

}

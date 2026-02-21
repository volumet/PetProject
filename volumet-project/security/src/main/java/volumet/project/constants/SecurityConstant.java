package volumet.project.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecurityConstant {
    public static final String BEARER_AUTH = "bearerAuth";
    public static final String JWT = "JWT";
    public static final String SCHEME_BEARER = "bearer";
    public static final String REALM_ACCESS = "realm_access";
    public static final String ROLE_CLAIMS = "roles";
    public static final String ROLE_PREFIX = "ROLE_";
    public static final String FAMILY_NAME ="family_name";
    public static final String GIVEN_NAME ="given_name";
    public static final String APP_JSON_CONTENT_TYPE = "application/json";
}

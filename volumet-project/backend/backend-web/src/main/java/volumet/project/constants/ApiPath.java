package volumet.project.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiPath {
    public static final String API_BASE = "/api";
    public static final String API_V1 = API_BASE + "/v1";

    // Manager api
    public static final String MANAGER_API = "managerApi";
    public static final String MANAGER_MAPPING = "/manage";
    public static final String MANAGER_CREATE_ROOM = "/createRoom";
}

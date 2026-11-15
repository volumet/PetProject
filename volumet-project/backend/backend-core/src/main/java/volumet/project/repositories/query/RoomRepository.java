package volumet.project.repositories.query;

import volumet.project.cache.VersionedValue;
import volumet.project.dtos.response.RoomResponseDto;

public interface RoomRepository {

    /**
     * The room's API DTO plus its JPA {@code @Version}, read in one query. The version stays outside the DTO:
     * it is cache metadata (orders cache writes), not part of the API response.
     *
     * @return null if the room doesn't exist
     */
    VersionedValue<RoomResponseDto> findVersionedById(Long id);
}

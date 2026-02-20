package volumet.project.repositories.query;

import volumet.project.dtos.response.RoomResponseDto;

public interface RoomRepository {
    RoomResponseDto getRoomById(Long id);
}

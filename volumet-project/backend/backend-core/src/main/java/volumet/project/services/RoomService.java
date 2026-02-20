package volumet.project.services;

import org.springframework.security.access.prepost.PreAuthorize;
import volumet.project.dtos.request.RoomCreationRequestDto;
import volumet.project.dtos.response.RoomResponseDto;

public interface RoomService {
    @PreAuthorize("hasRole('ADMIN')")
    void createRoom(RoomCreationRequestDto dto);

    RoomResponseDto findById(Long id);
}

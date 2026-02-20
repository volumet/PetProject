package volumet.project.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import volumet.project.dtos.request.RoomCreationRequestDto;
import volumet.project.dtos.response.RoomResponseDto;
import volumet.project.mappers.RoomMapper;
import volumet.project.repositories.jpa.RoomJpaRepository;
import volumet.project.repositories.query.RoomRepository;
import volumet.project.services.RoomService;

@Service
@RequiredArgsConstructor
@Transactional
public class RoomServiceImpl implements RoomService {
    private final RoomJpaRepository roomJpaRepository;
    private final RoomRepository roomRepository;
    private final RoomMapper roomMapper;

    @Override
    public void createRoom(RoomCreationRequestDto dto) {
        roomJpaRepository.save(roomMapper.toEntity(dto));
    }

    @Override
    @Transactional(readOnly = true)
    public RoomResponseDto findById(Long id) {
        return roomRepository.getRoomById(id);
    }
}

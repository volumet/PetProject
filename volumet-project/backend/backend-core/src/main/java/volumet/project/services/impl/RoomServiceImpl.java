package volumet.project.services.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import volumet.project.components.CacheStampedeGuard;
import volumet.project.dtos.request.RoomCreationRequestDto;
import volumet.project.dtos.response.RoomResponseDto;
import volumet.project.mappers.RoomMapper;
import volumet.project.repositories.jpa.RoomJpaRepository;
import volumet.project.repositories.query.RoomRepository;
import volumet.project.services.RoomService;

import static volumet.project.constants.CacheKeySpaces.ROOMS;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomServiceImpl implements RoomService {
    RoomJpaRepository roomJpaRepository;
    RoomRepository roomRepository;
    RoomMapper roomMapper;
    CacheStampedeGuard cacheStampedeGuard;

    @Override
    @Transactional
    public void createRoom(RoomCreationRequestDto dto) {
        roomJpaRepository.save(roomMapper.toEntity(dto));
    }

    /**
     * Hot path at semester start. Redis cache → per-instance request coalescing → Redis distributed lock →
     * resilience4j-guarded DB read (see CacheStampedeGuard and RoomRepositoryImpl#findVersionedById).
     * Not transactional on purpose: no DB connection is held while waiting on the cache, coalescer or lock;
     * the DB read opens its own read-only transaction.
     */
    @Override
    public RoomResponseDto findById(Long id) {
        return cacheStampedeGuard.get(ROOMS.key(id), RoomResponseDto.class, () -> roomRepository.findVersionedById(id));
    }
}

package volumet.project.repositories.impl;

import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import volumet.project.cache.VersionedValue;
import volumet.project.domains.QRoom;
import volumet.project.dtos.response.QRoomResponseDto;
import volumet.project.dtos.response.RoomResponseDto;
import volumet.project.repositories.query.RoomRepository;

import static volumet.project.repositories.support.VersionedProjections.versioned;

@Repository
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomRepositoryImpl implements RoomRepository {
    public static final String ROOM_READ = "roomRead";

    JPAQueryFactory queryFactory;

    /**
     * Protected DB read (config in application.yml under resilience4j.*.instances.roomRead).
     * Aspect order, outermost first: Retry → CircuitBreaker → Bulkhead → transaction, so each retry
     * attempt counts in the circuit breaker, waits for a bulkhead slot and runs in its own transaction.
     */
    @Override
    @Retry(name = ROOM_READ)
    @CircuitBreaker(name = ROOM_READ)
    @Bulkhead(name = ROOM_READ)
    @Transactional(readOnly = true)
    public VersionedValue<RoomResponseDto> findVersionedById(Long id) {
        QRoom room = QRoom.room;
        return queryFactory
                .select(versioned(new QRoomResponseDto(room.id, room.size, room.roomName), room.version))
                .from(room)
                .where(room.id.eq(id))
                .fetchFirst();
    }
}

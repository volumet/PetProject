package volumet.project.repositories.impl;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import volumet.project.domains.QRoom;
import volumet.project.dtos.response.QRoomResponseDto;
import volumet.project.dtos.response.RoomResponseDto;
import volumet.project.repositories.query.RoomRepository;

@Repository
@RequiredArgsConstructor
public class RoomRepositoryImpl implements RoomRepository {
    private final JPAQueryFactory queryFactory;

    @Override
    public RoomResponseDto getRoomById(Long id) {
        QRoom room = QRoom.room;
        return queryFactory
                .select(new QRoomResponseDto(room.id, room.size, room.roomName))
                .from(room)
                .where(room.id.eq(id))
                .fetchFirst();
    }
}

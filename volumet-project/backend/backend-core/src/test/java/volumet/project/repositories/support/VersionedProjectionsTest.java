package volumet.project.repositories.support;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.FactoryExpression;
import com.querydsl.jpa.impl.JPAQuery;
import org.junit.jupiter.api.Test;
import volumet.project.cache.VersionedValue;
import volumet.project.domains.QRoom;
import volumet.project.dtos.response.QRoomResponseDto;
import volumet.project.dtos.response.RoomResponseDto;

import static org.assertj.core.api.Assertions.assertThat;
import static volumet.project.repositories.support.VersionedProjections.versioned;

/**
 * Runs the projection through QueryDSL's JPAQuery (no database): the nested DTO projection must be flattened
 * into one select list, and each result row rebuilt into VersionedValue(dto, version).
 */
class VersionedProjectionsTest {
    private final QRoom room = QRoom.room;

    private JPAQuery<VersionedValue<RoomResponseDto>> query() {
        return new JPAQuery<Void>()
                .select(versioned(new QRoomResponseDto(room.id, room.size, room.roomName), room.version))
                .from(room)
                .where(room.id.eq(42L));
    }

    @Test
    void selectsDtoColumnsAndVersionInOneQuery() {
        assertThat(query().toString())
                .startsWith("select room.id, room.size, room.roomName, room.version")
                .contains("from Room room")
                .contains("where room.id = ?1");
    }

    @Test
    void eachRowBecomesAVersionedValueWithAPlainDto() {
        @SuppressWarnings("unchecked")
        FactoryExpression<VersionedValue<RoomResponseDto>> projection =
                (FactoryExpression<VersionedValue<RoomResponseDto>>) query().getMetadata().getProjection();

        // what the JPA query hands back per row: the flattened columns
        assertThat(projection.getArgs()).extracting(Expression::toString)
                .containsExactly("room.id", "room.size", "room.roomName", "room.version");
        VersionedValue<RoomResponseDto> row = projection.newInstance(42L, 30, "A1", 3L);

        assertThat(row.version()).isEqualTo(3L);
        assertThat(row.value().id()).isEqualTo(42L);
        assertThat(row.value().size()).isEqualTo(30);
        assertThat(row.value().roomName()).isEqualTo("A1");
    }
}

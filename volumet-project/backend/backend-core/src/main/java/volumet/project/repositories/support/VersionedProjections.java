package volumet.project.repositories.support;

import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.FactoryExpression;
import com.querydsl.core.types.Projections;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import volumet.project.cache.VersionedValue;

/**
 * QueryDSL projections into {@link VersionedValue}: the API DTO and the entity's {@code @Version} come out of
 * one query, typed, without putting the version into the DTO.
 * <pre>
 * queryFactory
 *     .select(versioned(new QRoomResponseDto(room.id, room.size, room.roomName), room.version))
 *     .from(room)
 *     .where(room.id.eq(id))
 *     .fetchFirst();   // VersionedValue&lt;RoomResponseDto&gt;, or null if not found
 * </pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VersionedProjections {

    /**
     * Matches VersionedValue's canonical constructor after erasure: (Object value, long version).
     * Given explicitly so QueryDSL doesn't have to infer it from the argument types (DTO class, Long).
     */
    private static final Class<?>[] CONSTRUCTOR_PARAMETERS = {Object.class, long.class};

    /**
     * @param value   the DTO projection, typically the generated {@code new QXxxDto(...)} (nested projections are
     *                flattened into the select list and rebuilt per row by QueryDSL)
     * @param version the entity's {@code @Version} path, e.g. {@code room.version}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> ConstructorExpression<VersionedValue<T>> versioned(FactoryExpression<T> value,
                                                                         Expression<Long> version) {
        return (ConstructorExpression) Projections.constructor(
                VersionedValue.class, CONSTRUCTOR_PARAMETERS, value, version);
    }
}

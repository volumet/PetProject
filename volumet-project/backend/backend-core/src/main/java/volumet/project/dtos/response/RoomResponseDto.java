package volumet.project.dtos.response;

import com.querydsl.core.annotations.QueryProjection;

import java.io.Serializable;

/** Room as returned by the API (and cached in Redis as JSON). */
public record RoomResponseDto(Long id, Integer size, String roomName) implements Serializable {

    /** Canonical constructor, declared only to generate QRoomResponseDto for QueryDSL projections. */
    @QueryProjection
    public RoomResponseDto {
    }
}

package volumet.project.dtos.response;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
public class RoomResponseDto implements Serializable {
    private Long id;
    private Integer size;
    private String roomName;

    @QueryProjection
    public RoomResponseDto(Long id, Integer size, String roomName) {
        this.id = id;
        this.size = size;
        this.roomName = roomName;
    }
}

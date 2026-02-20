package volumet.project.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import volumet.project.annotations.IgnoreAuditable;
import volumet.project.annotations.config.EnforcedMapperConfig;
import volumet.project.domains.Room;
import volumet.project.dtos.request.RoomCreationRequestDto;
import volumet.project.dtos.response.RoomResponseDto;

@Mapper(config = EnforcedMapperConfig.class)
public interface RoomMapper {
    @IgnoreAuditable
    @Mapping(target = "registrations", ignore = true)
    Room toEntity(RoomCreationRequestDto dto);

    RoomResponseDto toDto(Room entity);
}
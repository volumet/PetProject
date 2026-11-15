package volumet.project.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import volumet.project.annotations.IgnoreAuditable;
import volumet.project.annotations.config.EnforcedMapperConfig;
import volumet.project.domains.CivilProfile;
import volumet.project.dtos.request.PersonCreationRequestDto;

@Mapper(config = EnforcedMapperConfig.class)
public interface CivilProfileMapper {
    @IgnoreAuditable
    @Mapping(target = "person", ignore = true)
    CivilProfile toEntity(PersonCreationRequestDto dto);
}

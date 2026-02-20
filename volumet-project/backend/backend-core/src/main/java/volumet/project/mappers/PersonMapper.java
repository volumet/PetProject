package volumet.project.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import volumet.project.annotations.IgnoreAuditable;
import volumet.project.annotations.config.EnforcedMapperConfig;
import volumet.project.domains.Person;
import volumet.project.dtos.request.PersonCreationRequestDto;

@Mapper(config = EnforcedMapperConfig.class)
public interface PersonMapper {
    @IgnoreAuditable
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "students", ignore = true)
    @Mapping(target = "externalIdentifiers", ignore = true)
    Person toEntity(PersonCreationRequestDto dto);
}

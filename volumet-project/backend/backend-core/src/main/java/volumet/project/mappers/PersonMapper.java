package volumet.project.mappers;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import volumet.project.annotations.IgnoreAuditable;
import volumet.project.annotations.config.EnforcedMapperConfig;
import volumet.project.domains.Person;
import volumet.project.dtos.request.PersonCreationRequestDto;

@Mapper(config = EnforcedMapperConfig.class,
        uses = {CivilProfileMapper.class}
)
public interface PersonMapper {

    @IgnoreAuditable
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "students", ignore = true)
    @Mapping(target = "externalIdentifiers", ignore = true)
    @Mapping(target = "civilProfile", source = "dto")
    Person toEntity(PersonCreationRequestDto dto);

    @AfterMapping
    default void link(@MappingTarget Person person) {
        if (person.getCivilProfile() != null) {
            person.addCivilProfile(person.getCivilProfile());
        }
    }
}

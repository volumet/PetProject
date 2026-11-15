package volumet.project.dtos;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import volumet.project.dto.ExternalIdentifierDto;
import volumet.project.dtos.request.PersonCreationRequestDto;
import volumet.project.dtos.request.RoomCreationRequestDto;
import volumet.project.dtos.response.RoomResponseDto;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What changes at runtime when the DTOs are records: validation, JSON in and out, the Redis cache JSON,
 * and the SpEL cache key built from a record.
 */
class DtoRecordsTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // ---- validation (used to throw UnexpectedTypeException: @NotBlank on Integer / LocalDate) ----

    @Test
    void validRoomRequestHasNoViolations() {
        assertThat(validator.validate(new RoomCreationRequestDto(30, "A1"))).isEmpty();
    }

    @Test
    void roomRequestRejectsMissingOrNonPositiveSizeAndBlankName() {
        assertThat(paths(validator.validate(new RoomCreationRequestDto(null, "A1")))).containsExactly("size");
        assertThat(paths(validator.validate(new RoomCreationRequestDto(0, "A1")))).containsExactly("size");
        assertThat(paths(validator.validate(new RoomCreationRequestDto(30, " ")))).containsExactly("roomName");
    }

    @Test
    void validPersonRequestHasNoViolations() {
        assertThat(validator.validate(person(LocalDate.of(2000, 1, 1), "+84901234567"))).isEmpty();
    }

    @Test
    void personRequestRejectsMissingOrFutureDateOfBirth() {
        assertThat(paths(validator.validate(person(null, null)))).containsExactly("dateOfBirth");
        assertThat(paths(validator.validate(person(LocalDate.now().plusDays(1), null)))).containsExactly("dateOfBirth");
    }

    /** Used to be silently ignored: @Phone lacked @Constraint, so it was not a constraint at all. */
    @Test
    void personRequestRejectsInvalidPhoneNumber() {
        Set<ConstraintViolation<PersonCreationRequestDto>> violations =
                validator.validate(person(LocalDate.of(2000, 1, 1), "not-a-phone"));

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath()).hasToString("phoneNumber");
            assertThat(violation.getMessage()).isEqualTo("Invalid phone number format");
        });
    }

    @Test
    void phoneIsOptional() {
        assertThat(validator.validate(person(LocalDate.of(2000, 1, 1), null))).isEmpty();
    }

    // ---- JSON ----

    @Test
    void requestBodyDeserializesIntoRecord() throws Exception {
        RoomCreationRequestDto dto = objectMapper.readValue("{\"size\":30,\"roomName\":\"A1\"}", RoomCreationRequestDto.class);

        assertThat(dto).isEqualTo(new RoomCreationRequestDto(30, "A1"));
    }

    @Test
    void responseSerializesOnlyApiFields() throws Exception {
        assertThat(objectMapper.writeValueAsString(new RoomResponseDto(42L, 30, "A1")))
                .isEqualTo("{\"id\":42,\"size\":30,\"roomName\":\"A1\"}");
    }

    @Test
    void errorResponseSerializesItsFields() throws Exception {
        assertThat(objectMapper.writeValueAsString(new ErrorResponseDto("FORBIDDEN", "Access denied", "abc")))
                .isEqualTo("{\"errorCode\":\"FORBIDDEN\",\"message\":\"Access denied\",\"correlationId\":\"abc\"}");
    }

    /** The Redis cache (RedisVersionedCache) stores values with this serializer. */
    @Test
    void cachedRecordSurvivesRedisJsonRoundTrip() {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        RoomResponseDto room = new RoomResponseDto(42L, 30, "A1");

        assertThat(serializer.deserialize(serializer.serialize(room), RoomResponseDto.class)).isEqualTo(room);
    }

    // ---- SpEL cache key (DomainIdentifierResolverImpl @Cacheable) ----

    @Test
    void cacheKeyExpressionReadsRecordAccessors() {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("externalIdentifierDto", new ExternalIdentifierDto("https://kc/realms/r", "sub-1", "A", "B"));

        Object key = new SpelExpressionParser()
                .parseExpression("#externalIdentifierDto.issuer() + ':' + #externalIdentifierDto.subject()")
                .getValue(context);

        assertThat(key).isEqualTo("https://kc/realms/r:sub-1");
    }

    private static PersonCreationRequestDto person(LocalDate dateOfBirth, String phone) {
        return new PersonCreationRequestDto("user", "secret", "First", "Last", dateOfBirth, null, "a@b.co", phone);
    }

    private static Set<String> paths(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
    }
}

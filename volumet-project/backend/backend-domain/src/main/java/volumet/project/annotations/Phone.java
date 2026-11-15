package volumet.project.annotations;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * E.164 phone number (optional leading '+', up to 15 digits, no leading 0). Null is valid; combine with
 * {@code @NotBlank} to make it required.
 * <p>
 * A composed constraint: {@code @Constraint(validatedBy = {})} plus message/groups/payload are what make Bean
 * Validation apply the {@code @Pattern} below; without them the annotation is silently ignored.
 */
@Pattern(regexp = "^\\+?[1-9]\\d{1,14}$")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
public @interface Phone {
    String message() default "Invalid phone number format";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

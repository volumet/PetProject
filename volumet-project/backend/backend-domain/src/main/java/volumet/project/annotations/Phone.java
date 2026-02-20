package volumet.project.annotations;

import jakarta.validation.constraints.Pattern;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Pattern(
        regexp = "^\\+?[1-9]\\d{1,14}$",
        message = "Invalid phone number format"
)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Phone {
}

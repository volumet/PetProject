package volumet.project.annotations;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AutoConfiguration(
        before = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        }
)
public @interface SecurityAutoConfiguration {
}

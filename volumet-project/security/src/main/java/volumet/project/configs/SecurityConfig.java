package volumet.project.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import volumet.project.annotations.DevProfile;
import volumet.project.annotations.ProdProfile;
import volumet.project.annotations.SecurityAutoConfiguration;
import volumet.project.utils.PermissionUtils;

import java.io.IOException;

import static volumet.project.constants.SecurityConstant.APP_JSON_CONTENT_TYPE;
import static volumet.project.constants.SecurityConstant.BEARER_AUTH;
import static volumet.project.constants.SecurityConstant.JWT;
import static volumet.project.constants.SecurityConstant.SCHEME_BEARER;

@SecurityAutoConfiguration
@EnableMethodSecurity
@ConditionalOnProperty(
        prefix = "app.security",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SecurityConfig {

    /**
     * Enable oauth jwt authentication. <br/>
     */
    @Bean
    @ProdProfile
    public SecurityFilterChain securedFilterChain(HttpSecurity http,
                                                  Converter<Jwt, AbstractAuthenticationToken> converter,
                                                  AuthenticationEntryPoint authEntryPoint,
                                                  AccessDeniedHandler accessDeniedHandler) throws Exception {
        initCommonSecurityChain(http, converter, authEntryPoint, accessDeniedHandler);
        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());

        return http.build();
    }

    /**
     * Disable csrf check for dev mode. <br/>
     * Permit access for swagger. <br/>
     * Check for dev secret key jwt. <br/>
     */
    @Bean
    @DevProfile
    public SecurityFilterChain limitedSecurityFilterChain(HttpSecurity http,
                                                          Converter<Jwt, AbstractAuthenticationToken> converter,
                                                          AuthenticationEntryPoint authEntryPoint,
                                                          AccessDeniedHandler accessDeniedHandler) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);

        initCommonSecurityChain(http, converter, authEntryPoint, accessDeniedHandler);

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/api-docs",
                        "/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**"
                ).permitAll()
                .anyRequest().authenticated());

        return http.build();
    }

    private void initCommonSecurityChain(HttpSecurity http,
                                         Converter<Jwt, AbstractAuthenticationToken> converter,
                                         AuthenticationEntryPoint authEntryPoint,
                                         AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
    }

    @Bean
    public AuthenticationEntryPoint customAuthEntryPoint(ObjectMapper objectMapper) {
        return (_, response, _) -> writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required", objectMapper);
    }

    @Bean
    public AccessDeniedHandler customAccessDeniedHandler(ObjectMapper objectMapper) {
        return (_, response, _) -> writeError(response, HttpStatus.FORBIDDEN, "Access denied", objectMapper);
    }

    private void writeError(HttpServletResponse response,
                                   HttpStatus status,
                                   String message,
                                   ObjectMapper objectMapper
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(APP_JSON_CONTENT_TYPE);
        objectMapper.writeValue(response.getOutputStream(), PermissionUtils.buildErrorResponse(message, status));
    }

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme(SCHEME_BEARER)
                                        .bearerFormat(JWT)
                        )
                )
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

}
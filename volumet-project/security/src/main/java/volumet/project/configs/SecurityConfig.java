package volumet.project.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import volumet.project.annotations.SecurityAutoConfiguration;
import volumet.project.components.PostSecurityFilter;
import volumet.project.configs.properties.VolumetSecurityProperties;
import volumet.project.handlers.JsonAccessDeniedHandler;
import volumet.project.handlers.JsonAuthenticationEntryPoint;

import java.util.ArrayList;
import java.util.List;

import static volumet.project.constants.SecurityConstant.BEARER_AUTH;
import static volumet.project.constants.SecurityConstant.JWT;
import static volumet.project.constants.SecurityConstant.SCHEME_BEARER;

/**
 * The one security filter chain of every service: stateless Keycloak JWT resource server.
 * <p>
 * Deliberately independent of profiles and toggles: the same rules apply in every environment, and what used
 * to differ per profile now follows the actual configuration (Swagger is open only while springdoc is enabled,
 * CORS only when origins are configured). Method security is always on ({@link MethodSecurityConfig}).
 * <ul>
 *   <li>public: the actuator endpoints in volumet.security.actuator.public-endpoints (default health, info: probes,
 *       load balancers) and the API docs while springdoc is enabled</li>
 *   <li>ADMIN: every other actuator endpoint (metrics, circuit breakers, ... reveal internals)</li>
 *   <li>authenticated: everything else; finer rules via {@code @PreAuthorize}</li>
 * </ul>
 */
@SecurityAutoConfiguration
@EnableConfigurationProperties(VolumetSecurityProperties.class)
public class SecurityConfig {
    private static final String ADMIN_ROLE = "ADMIN";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
                                                   AuthenticationEntryPoint authenticationEntryPoint,
                                                   AccessDeniedHandler accessDeniedHandler,
                                                   ObjectProvider<PostSecurityFilter> postSecurityFilters,
                                                   VolumetSecurityProperties properties,
                                                   Environment environment) throws Exception {
        http
                // stateless bearer-token API: no session, no cookies, so no CSRF exposure
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        // also for invalid/expired tokens, which never reach exceptionHandling()
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> authorize(auth, properties.getActuator(), environment));

        List<String> allowedOrigins = properties.getCors().getAllowedOrigins();
        if (!allowedOrigins.isEmpty()) {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource(properties.getCors())));
        }

        postSecurityFilters.orderedStream()
                .forEach(filter -> http.addFilterAfter(filter, BearerTokenAuthenticationFilter.class));
        return http.build();
    }

    private static void authorize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth,
                                  VolumetSecurityProperties.Actuator actuatorProperties,
                                  Environment environment) {
        String actuator = environment.getProperty("management.endpoints.web.base-path", "/actuator");
        List<String> publicEndpoints = actuatorProperties.getPublicEndpoints().stream()
                .flatMap(id -> java.util.stream.Stream.of(actuator + "/" + id, actuator + "/" + id + "/**"))
                .toList();
        if (!publicEndpoints.isEmpty()) {
            auth.requestMatchers(publicEndpoints.toArray(String[]::new)).permitAll();
        }

        List<String> docs = apiDocPaths(environment);
        if (!docs.isEmpty()) {
            auth.requestMatchers(docs.toArray(String[]::new)).permitAll();
        }

        auth.requestMatchers(actuator + "/**").hasRole(ADMIN_ROLE)
                .anyRequest().authenticated();
    }

    /** Paths springdoc actually serves (its own settings), only while it is enabled. */
    private static List<String> apiDocPaths(Environment environment) {
        List<String> paths = new ArrayList<>();
        if (environment.getProperty("springdoc.api-docs.enabled", Boolean.class, true)) {
            String apiDocs = environment.getProperty("springdoc.api-docs.path", "/v3/api-docs");
            paths.add(apiDocs);
            paths.add(apiDocs + "/**");

            if (environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, true)) {
                String swaggerUi = environment.getProperty("springdoc.swagger-ui.path", "/swagger-ui.html");
                paths.add(swaggerUi);
                paths.add(swaggerUi + "/**");
                paths.add("/swagger-ui/**"); // UI assets, always served from here
            }
        }
        return paths;
    }

    private static UrlBasedCorsConfigurationSource corsConfigurationSource(VolumetSecurityProperties.Cors properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.getAllowedOrigins());
        cors.setAllowedMethods(properties.getAllowedMethods());
        cors.setAllowedHeaders(properties.getAllowedHeaders());
        cors.setExposedHeaders(properties.getExposedHeaders());
        cors.setMaxAge(properties.getMaxAge());
        cors.setAllowCredentials(false); // bearer tokens travel in a header, never cookies

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticationEntryPoint.class)
    public JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new JsonAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(AccessDeniedHandler.class)
    public JsonAccessDeniedHandler jsonAccessDeniedHandler(ObjectMapper objectMapper) {
        return new JsonAccessDeniedHandler(objectMapper);
    }

    /**
     * Bearer-token "Authorize" button in Swagger UI, whenever springdoc is enabled.
     */
    @Bean
    @ConditionalOnClass(OpenAPI.class)
    @ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(OpenAPI.class)
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

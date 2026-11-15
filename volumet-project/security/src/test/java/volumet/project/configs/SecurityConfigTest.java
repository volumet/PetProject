package volumet.project.configs;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The real security chain (SecurityConfig + AuthenticationConfig + MethodSecurityConfig + Spring Boot's
 * security and resource-server auto-configuration) through MockMvc. A stub JwtDecoder turns test tokens
 * into Keycloak-shaped JWTs; actuator and API-docs paths are served by a stub controller, since the rules
 * under test are path-based.
 */
class SecurityConfigTest {

    @RestController
    static class Api {
        @GetMapping("/api/v1/rooms") String read() { return "ok"; }
        @PostMapping("/api/v1/rooms") String write() { return "created"; }
        @PostMapping("/api/v1/admin") @PreAuthorize("hasRole('ADMIN')") String admin() { return "admin ok"; }
        @GetMapping({"/actuator/health", "/actuator/health/liveness", "/actuator/info", "/actuator/metrics"}) String actuator() { return "actuator"; }
        @GetMapping({"/v3/api-docs", "/api-docs"}) String docs() { return "docs"; }
    }

    /** Tokens: user / admin (realm roles), client-admin / other-client-admin (client roles), anything else is invalid. */
    @Configuration(proxyBeanMethods = false)
    static class KeycloakTokens {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                Jwt.Builder jwt = Jwt.withTokenValue(token).header("alg", "RS256").subject(token)
                        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("scope", "openid profile");
                return switch (token) {
                    case "user" -> jwt.claim("realm_access", Map.of("roles", List.of("USER"))).build();
                    case "admin" -> jwt.claim("realm_access", Map.of("roles", List.of("ADMIN"))).build();
                    case "client-admin" -> jwt.claim("resource_access", Map.of("volumet-app", Map.of("roles", List.of("ADMIN")))).build();
                    case "other-client-admin" -> jwt.claim("resource_access", Map.of("other-app", Map.of("roles", List.of("ADMIN")))).build();
                    default -> throw new BadJwtException("Jwt expired");
                };
            };
        }
    }

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SecurityConfig.class, AuthenticationConfig.class, MethodSecurityConfig.class,
                    SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class,
                    JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
                    DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class))
            .withUserConfiguration(KeycloakTokens.class, Api.class);

    private static void withMvc(AssertableWebApplicationContext context, Consumer<MockMvc> test) {
        assertThat(context).hasNotFailed();
        test.accept(MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build());
    }

    private static MockHttpServletResponse call(MockMvc mvc, MockHttpServletRequestBuilder request, String token) {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            return mvc.perform(request).andReturn().getResponse();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ---- #2: one chain, no profile needed ----

    @Test
    void worksWithoutAnyProfileUsingOneChain() {
        runner.run(context -> withMvc(context, mvc -> {
            assertThat(context.getBeansOfType(SecurityFilterChain.class)).containsOnlyKeys("securityFilterChain");
            assertThat(call(mvc, get("/api/v1/rooms"), "user").getStatus()).isEqualTo(200);
            assertThat(call(mvc, post("/api/v1/admin"), "admin").getStatus()).isEqualTo(200);
        }));
    }

    @Test
    void sameBehaviourWithDevOrProdProfileActive() {
        for (String profile : List.of("dev", "prod", "dev,prod")) {
            runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> withMvc(context, mvc -> {
                assertThat(call(mvc, post("/api/v1/admin"), "user").getStatus()).isEqualTo(403);
                assertThat(call(mvc, post("/api/v1/admin"), "admin").getStatus()).isEqualTo(200);
            }));
        }
    }

    // ---- #1: authorization can't be switched off ----

    @Test
    void methodSecurityStaysOnEvenWithTheOldToggle() {
        runner.withPropertyValues("app.security.enabled=false").run(context -> withMvc(context, mvc ->
                assertThat(call(mvc, post("/api/v1/admin"), "user").getStatus()).isEqualTo(403)));
    }

    // ---- #3: unrelated Converter beans don't switch off the Keycloak converter ----

    static class TrimConverter implements Converter<String, String> {
        @Override
        public String convert(String source) {
            return source.trim();
        }
    }

    @Test
    void unrelatedConverterBeanDoesNotBreakStartup() {
        runner.withBean("trimConverter", TrimConverter.class, TrimConverter::new).run(context -> withMvc(context, mvc -> {
            assertThat(context).hasBean(AuthenticationConfig.JWT_AUTHENTICATION_CONVERTER);
            assertThat(call(mvc, post("/api/v1/admin"), "admin").getStatus()).isEqualTo(200);
        }));
    }

    // ---- #4: actuator ----

    @Test
    void healthAndInfoArePublicOtherActuatorEndpointsNeedAdmin() {
        runner.run(context -> withMvc(context, mvc -> {
            assertThat(call(mvc, get("/actuator/health"), null).getStatus()).isEqualTo(200);
            assertThat(call(mvc, get("/actuator/health/liveness"), null).getStatus()).isEqualTo(200);
            assertThat(call(mvc, get("/actuator/info"), null).getStatus()).isEqualTo(200);
            assertThat(call(mvc, get("/actuator/metrics"), null).getStatus()).isEqualTo(401);
            assertThat(call(mvc, get("/actuator/metrics"), "user").getStatus()).isEqualTo(403);
            assertThat(call(mvc, get("/actuator/metrics"), "admin").getStatus()).isEqualTo(200);
        }));
    }

    @Test
    void publicActuatorEndpointsAreConfigurable() {
        runner.withPropertyValues("volumet.security.actuator.public-endpoints=health,metrics").run(context -> withMvc(context, mvc -> {
            assertThat(call(mvc, get("/actuator/metrics"), null).getStatus()).isEqualTo(200);
            assertThat(call(mvc, get("/actuator/info"), null).getStatus()).isEqualTo(401);
        }));
    }

    // ---- #5: consistent 401 / 403 ----

    @Test
    void missingTokenGets401WithHeaderAndJson() {
        runner.run(context -> withMvc(context, mvc -> {
            MockHttpServletResponse response = call(mvc, get("/api/v1/rooms"), null);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
            assertThat(body(response)).contains("\"errorCode\":\"UNAUTHORIZED\"", "\"message\":\"Authentication required\"");
        }));
    }

    @Test
    void invalidOrExpiredTokenGets401WithHeaderAndJson() {
        runner.run(context -> withMvc(context, mvc -> {
            MockHttpServletResponse response = call(mvc, get("/api/v1/rooms"), "expired");
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer error=\"invalid_token\"");
            assertThat(body(response)).contains("\"errorCode\":\"UNAUTHORIZED\"", "\"message\":\"Invalid or expired token\"");
        }));
    }

    @Test
    void missingRoleGets403WithHeaderAndJson() {
        runner.run(context -> withMvc(context, mvc -> {
            MockHttpServletResponse response = call(mvc, post("/api/v1/admin"), "user");
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer error=\"insufficient_scope\"");
            assertThat(body(response)).contains("\"errorCode\":\"FORBIDDEN\"", "\"message\":\"Access denied\"");
        }));
    }

    // ---- #6: stateless, no CSRF ----

    @Test
    void writesWithBearerTokenNeedNoCsrfToken() {
        runner.run(context -> withMvc(context, mvc ->
                assertThat(call(mvc, post("/api/v1/rooms"), "user").getStatus()).isEqualTo(200)));
    }

    // ---- #7: client roles of this service only ----

    @Test
    void clientRolesOfThisServiceCountWhenClientIdIsConfigured() {
        runner.withPropertyValues("volumet.security.keycloak.client-id=volumet-app").run(context -> withMvc(context, mvc -> {
            assertThat(call(mvc, post("/api/v1/admin"), "client-admin").getStatus()).isEqualTo(200);
            assertThat(call(mvc, post("/api/v1/admin"), "other-client-admin").getStatus()).isEqualTo(403);
        }));
    }

    @Test
    void clientRolesAreIgnoredWithoutClientId() {
        runner.run(context -> withMvc(context, mvc ->
                assertThat(call(mvc, post("/api/v1/admin"), "client-admin").getStatus()).isEqualTo(403)));
    }

    // ---- #8: CORS only when configured ----

    @Test
    void corsIsOffByDefault() {
        runner.run(context -> withMvc(context, mvc -> {
            MockHttpServletResponse response = call(mvc, preflight("https://app.example.com"), null);
            assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        }));
    }

    @Test
    void corsAllowsOnlyConfiguredOrigins() {
        runner.withPropertyValues("volumet.security.cors.allowed-origins=https://app.example.com").run(context -> withMvc(context, mvc -> {
            MockHttpServletResponse allowed = call(mvc, preflight("https://app.example.com"), null);
            assertThat(allowed.getStatus()).isEqualTo(200);
            assertThat(allowed.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://app.example.com");

            MockHttpServletResponse denied = call(mvc, preflight("https://evil.example.com"), null);
            assertThat(denied.getStatus()).isEqualTo(403);
            assertThat(denied.getHeader("Access-Control-Allow-Origin")).isNull();
        }));
    }

    // ---- #9 / API docs follow springdoc's own settings ----

    @Test
    void apiDocsArePublicWhileSpringdocIsEnabled() {
        runner.run(context -> withMvc(context, mvc ->
                assertThat(call(mvc, get("/v3/api-docs"), null).getStatus()).isEqualTo(200)));
    }

    @Test
    void apiDocsFollowTheConfiguredPath() {
        runner.withPropertyValues("springdoc.api-docs.path=/api-docs").run(context -> withMvc(context, mvc -> {
            assertThat(call(mvc, get("/api-docs"), null).getStatus()).isEqualTo(200);
            assertThat(call(mvc, get("/v3/api-docs"), null).getStatus()).isEqualTo(401);
        }));
    }

    @Test
    void apiDocsNeedAuthenticationWhenSpringdocIsDisabled() {
        runner.withPropertyValues("springdoc.api-docs.enabled=false").run(context -> withMvc(context, mvc ->
                assertThat(call(mvc, get("/v3/api-docs"), null).getStatus()).isEqualTo(401)));
    }

    private static MockHttpServletRequestBuilder preflight(String origin) {
        return options("/api/v1/rooms")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization");
    }

    private static String body(MockHttpServletResponse response) {
        try {
            return response.getContentAsString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}

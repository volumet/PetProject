package volumet.project.converters;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("u")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    }

    private static List<String> authorities(KeycloakJwtAuthenticationConverter converter, Jwt jwt) {
        return converter.convert(jwt).getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void mapsRealmRolesOwnClientRolesAndScopes() {
        Jwt token = jwt()
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .claim("resource_access", Map.of(
                        "volumet-app", Map.of("roles", List.of("ADMIN")),
                        "other-app", Map.of("roles", List.of("SUPERUSER"))))
                .claim("scope", "openid profile")
                .build();

        assertThat(authorities(new KeycloakJwtAuthenticationConverter("volumet-app"), token))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN", "SCOPE_openid", "SCOPE_profile")
                .doesNotContain("ROLE_SUPERUSER");
    }

    @Test
    void withoutClientIdOnlyRealmRolesAndScopesCount() {
        Jwt token = jwt()
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .claim("resource_access", Map.of("volumet-app", Map.of("roles", List.of("ADMIN"))))
                .build();

        assertThat(authorities(new KeycloakJwtAuthenticationConverter(null), token)).containsExactly("ROLE_USER");
        assertThat(authorities(new KeycloakJwtAuthenticationConverter(" "), token)).containsExactly("ROLE_USER");
    }

    /** Previously an unchecked cast: a claim of an unexpected shape threw ClassCastException (a 500). */
    @Test
    void malformedRoleClaimsAreIgnoredInsteadOfFailing() {
        Jwt token = jwt()
                .claim("realm_access", Map.of("roles", "ADMIN"))                  // string, not a list
                .claim("resource_access", Map.of("volumet-app", List.of("ADMIN"))) // list, not {"roles": [...]}
                .build();

        assertThat(authorities(new KeycloakJwtAuthenticationConverter("volumet-app"), token)).isEmpty();
    }

    @Test
    void nonStringAndBlankRolesAreSkipped() {
        Jwt token = jwt().claim("realm_access", Map.of("roles", List.of("USER", 42, " "))).build();

        assertThat(authorities(new KeycloakJwtAuthenticationConverter(null), token)).containsExactly("ROLE_USER");
    }
}

package volumet.project.dto;

/** Identity of a Keycloak user as read from the JWT (iss + sub), plus name claims for user creation. */
public record ExternalIdentifierDto(String issuer, String subject, String firstName, String lastName) {
}

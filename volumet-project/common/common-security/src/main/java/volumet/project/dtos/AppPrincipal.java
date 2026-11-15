package volumet.project.dtos;

/** The app's own user identity (uuid), set as the authentication principal by UserIdentifierFilter. */
public record AppPrincipal(String uuid) {
}

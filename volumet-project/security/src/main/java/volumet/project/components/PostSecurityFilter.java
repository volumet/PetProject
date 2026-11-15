package volumet.project.components;

import jakarta.servlet.Filter;

/**
 * This interface will add defined filters after BearerTokenAuthenticationFilter.
 */
public interface PostSecurityFilter extends Filter {
}
package volumet.project.configs;

import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import volumet.project.dtos.AppPrincipal;

import java.util.Optional;

import static volumet.project.constants.PersistentConstant.SYSTEM_AUDITOR;

/**
 * Auto-configuration (common-persistence): fills createdBy / lastModifiedBy of AbstractAuditableEntity.
 * <ul>
 *   <li>With Spring Security + common-security on the classpath: the current user's AppPrincipal uuid
 *       (set by the security module's UserIdentifierFilter), else "SYSTEM".</li>
 *   <li>Without them: always "SYSTEM".</li>
 * </ul>
 * Backs off if the app enables JPA auditing itself or defines its own AuditorAware.
 * modifyOnCreate = false: lastModifiedBy / lastModifiedDate stay null until the first update.
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({EntityManagerFactory.class, EnableJpaAuditing.class})
@ConditionalOnBean(EntityManagerFactory.class)   // JPA classes alone aren't enough: auditing needs a configured JPA unit
@ConditionalOnMissingBean(name = "jpaAuditingHandler")
@EnableJpaAuditing(modifyOnCreate = false)
public class JpaAuditableConfig {

    @Slf4j
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({SecurityContextHolder.class, AppPrincipal.class})
    static class SecurityAuditorConfig {

        @Bean
        @ConditionalOnMissingBean(AuditorAware.class)
        AuditorAware<String> auditorAware() {
            return () -> {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.isAuthenticated()
                        && authentication.getPrincipal() instanceof AppPrincipal appPrincipal) {
                    return Optional.of(appPrincipal.uuid());
                }
                // background jobs, coalesced loads (no SecurityContext), user creation during resolution
                log.debug("No AppPrincipal in security context, auditing as {}", SYSTEM_AUDITOR);
                return Optional.of(SYSTEM_AUDITOR);
            };
        }
    }

    /**
     * Fallback when security isn't on the classpath. Nested configuration classes are processed before
     * this class's own bean methods, so SecurityAuditorConfig's auditor wins whenever it applies.
     */
    @Bean
    @ConditionalOnMissingBean(AuditorAware.class)
    AuditorAware<String> systemAuditorAware() {
        return () -> Optional.of(SYSTEM_AUDITOR);
    }
}

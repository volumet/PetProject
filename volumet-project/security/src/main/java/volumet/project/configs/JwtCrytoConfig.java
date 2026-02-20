package volumet.project.configs;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import volumet.project.annotations.DevProfile;
import volumet.project.configs.properties.SecurityKeyProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static volumet.project.constants.EncodingAlgorithm.HMAC_SHA_256;

/**
 * This configuration only exist to support dev process. <br/>
 * It will generate a secret key bean for local testing. <br/>
 */
@AutoConfiguration
@EnableConfigurationProperties(SecurityKeyProperties.class)
@DevProfile
public class JwtCrytoConfig {

    @Bean
    SecretKey secretKey(SecurityKeyProperties securityKeyProperties) {
        byte[] secretKeyBytes = Base64.getUrlDecoder().decode(securityKeyProperties.secretKey().getBytes());
        return new SecretKeySpec(secretKeyBytes, HMAC_SHA_256);
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey secretKey) {
        return NimbusJwtDecoder
                .withSecretKey(secretKey)
                .build();
    }
}
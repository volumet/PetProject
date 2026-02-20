package dev.tool;

import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class TokenGenerator {
    private static final Base64.Encoder URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    public static final String HMAC_SHA_256 = "HmacSHA256";

    public static void main(String[] args) throws Exception {
        String secretKey = System.getenv("DEV_JWT_SECRET_KEY");
        if (secretKey == null) {
            throw new IllegalStateException("Missing JWT secret key");
        }

        byte[] decodedKey = Base64.getUrlDecoder().decode(secretKey);

        System.out.println("DEV JWT TOKEN:");
        System.out.println(generate(decodedKey));
    }

    public static String generate(byte[] secretKey) throws NoSuchAlgorithmException, InvalidKeyException {
        Instant now = Instant.now();

        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("alg", "HS256");
        headers.put("typ", "JWT");

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", UUID.randomUUID().toString());
        claims.put("iss", "volumet-dev");
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusSeconds(3600).getEpochSecond());
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("realm_access", Map.of(
                "roles", List.of("USER")
        ));
        claims.put("env", "dev");
        claims.put("scope", "read write");

        String headerEncoded = base64Url(new JSONObject(headers).toString());
        String payloadEncoded = base64Url(new JSONObject(claims).toString());
        String input = String.join(".", headerEncoded, payloadEncoded);
        String signature = sign(input, secretKey);

        return String.join(".", input, signature);
    }

    private static String base64Url(String value) {
        return URL_ENCODER.encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sign(String data, byte[] secretKey) throws NoSuchAlgorithmException, InvalidKeyException {

        Mac mac = Mac.getInstance(HMAC_SHA_256);
        mac.init(new SecretKeySpec(secretKey, HMAC_SHA_256));

        byte[] signatureBytes =
                mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        return URL_ENCODER.encodeToString(signatureBytes);
    }
}

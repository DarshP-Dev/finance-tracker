package com.financetracker.backend.security;

import com.financetracker.backend.entities.User;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class JwtService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String secret;
    private final Duration expiration;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${app.security.jwt.secret:}") String secret,
            @Value("${app.security.jwt.expiration:PT1H}") Duration expiration
    ) {
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
        this.secret = secret;
        this.expiration = expiration;
    }

    public String generateToken(User user) {
        Instant now = Instant.now(clock);
        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.getEmail());
        payload.put("uid", user.getId());
        payload.put("username", user.getUsername());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plus(expiration).getEpochSecond());

        String unsignedToken = base64UrlEncode(toJson(header)) + "." + base64UrlEncode(toJson(payload));
        return unsignedToken + "." + sign(unsignedToken);
    }

    public String extractUsername(String token) {
        return extractClaim(token, "sub")
                .map(Object::toString)
                .orElse(null);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return parseClaims(token)
                .map(claims -> isExpectedSubject(claims, userDetails) && !isExpired(claims))
                .orElse(false);
    }

    @PostConstruct
    void validateConfiguration() {
        validateSecret();
    }

    private Optional<Object> extractClaim(String token, String claimName) {
        return parseClaims(token).map(claims -> claims.get(claimName));
    }

    private Optional<Map<String, Object>> parseClaims(String token) {
        try {
            return Optional.of(getClaims(token));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Map<String, Object> getClaims(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }

        String unsignedToken = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(sign(unsignedToken).getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }

        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(payload, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JWT payload", exception);
        }
    }

    private boolean isExpectedSubject(Map<String, Object> claims, UserDetails userDetails) {
        Object subject = claims.get("sub");
        return subject != null && subject.toString().equals(userDetails.getUsername());
    }

    private boolean isExpired(Map<String, Object> claims) {
        Object expirationClaim = claims.get("exp");

        if (!(expirationClaim instanceof Number expirationTime)) {
            return true;
        }

        return Instant.now(clock).getEpochSecond() >= expirationTime.longValue();
    }

    private byte[] toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize JWT content", exception);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return base64UrlEncode(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign JWT", exception);
        }
    }

    private String base64UrlEncode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
    }
}

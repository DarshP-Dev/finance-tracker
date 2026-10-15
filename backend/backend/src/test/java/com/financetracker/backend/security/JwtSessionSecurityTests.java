package com.financetracker.backend.security;

import static org.assertj.core.api.Assertions.*;
import com.financetracker.backend.entities.User;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import tools.jackson.databind.ObjectMapper;

class JwtSessionSecurityTests {
    private static final String SECRET="synthetic-test-secret-at-least-32-bytes";
    private final ObjectMapper mapper=new ObjectMapper();
    private final User user=User.builder().id(1L).email("user@example.invalid").username("user").password("test").build();
    private final Instant time=Instant.parse("2026-10-01T00:00:00Z");
    private JwtService jwt(Instant now) { return new JwtService(mapper,SECRET,Duration.ofMinutes(15),Clock.fixed(now,ZoneOffset.UTC)); }
    private AccountPrincipal principal(Long id) { return new AccountPrincipal(id,user.getEmail(),user.getPassword()); }
    @Test void validJwtHasCorrectExpiryAndRejectsExactExpirationBoundary() {
        String token=jwt(time).generateToken(user);
        assertThat(jwt(time).isTokenValid(token,principal(1L))).isTrue();
        assertThat(jwt(time.plusSeconds(899)).isTokenValid(token,principal(1L))).isTrue();
        assertThat(jwt(time.plusSeconds(900)).isTokenValid(token,principal(1L))).isFalse();
    }
    @Test void tokenIsBoundToImmutableOwnerEvenWhenEmailIsReused() {
        assertThat(jwt(time).isTokenValid(jwt(time).generateToken(user),principal(2L))).isFalse();
        assertThat(jwt(time).isTokenValid(jwt(time).generateToken(user),new AccountPrincipal(1L,"other@example.invalid","test"))).isFalse();
    }
    @Test void tamperedSignaturesAndMalformedTokensAreRejected() {
        String token=jwt(time).generateToken(user);
        assertThat(jwt(time).isTokenValid(token.substring(0,token.length()-8)+"tampered",principal(1L))).isFalse();
        for(String value:List.of("", "a.b.c", "a.b", "a".repeat(8193))) {
            assertThat(jwt(time).isTokenValid(value,principal(1L))).isFalse();
            assertThat(jwt(time).extractUsername(value)).isNull();
        }
        assertThat(jwt(time).isTokenValid(null,principal(1L))).isFalse();
    }
    @Test void correctlySignedUnsupportedAlgorithmHeaderIsStillRejected() throws Exception {
        String[] parts=jwt(time).generateToken(user).split("\\.");
        String unsigned=Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(Map.of("alg","none","typ","JWT")))+"."+parts[1];
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String signed=unsigned+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        assertThat(jwt(time).isTokenValid(signed,principal(1L))).isFalse();
    }
    @Test void invalidAccessAndRefreshLifetimesFailConfiguration() {
        assertThatThrownBy(() -> new JwtService(mapper,SECRET,Duration.ZERO,Clock.systemUTC()).validateConfiguration()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtService(mapper,"short",Duration.ofMinutes(15),Clock.systemUTC()).validateConfiguration()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new com.financetracker.backend.services.RefreshTokenService(null,null,null,Clock.systemUTC(),Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void httpsCookieDeletionIsSecureHttpOnlyHostOnlyAndScopedToAuth() {
        var request=new MockHttpServletRequest(); request.setSecure(true);
        var response=new MockHttpServletResponse(); new RefreshCookie("Lax","auto",Clock.systemUTC()).clear(request,response);
        assertThat(response.getHeader("Set-Cookie")).contains("Secure", "HttpOnly", "SameSite=Lax", "Max-Age=0", "Path=/api/auth").doesNotContain("Domain=");
    }
    @Test void unsafeCookieConfigurationsAreRejected() {
        assertThatThrownBy(() -> new RefreshCookie("None","auto",Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshCookie("Invalid","true",Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
        var request=new MockHttpServletRequest(); request.setSecure(true);
        assertThatThrownBy(() -> new RefreshCookie("Lax","false",Clock.systemUTC()).clear(request,new MockHttpServletResponse())).isInstanceOf(IllegalStateException.class);
    }
}

package com.financetracker.backend.security;

import com.financetracker.backend.services.RefreshTokenService.IssuedSession;
import jakarta.servlet.http.*;
import java.time.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookie {
    public static final String NAME = "financeTrackerRefresh";
    private final String sameSite;
    private final String secure;
    private final Clock clock;
    public RefreshCookie(@Value("${app.security.refresh.cookie.same-site:Lax}") String sameSite,
            @Value("${app.security.refresh.cookie.secure:auto}") String secure, Clock clock) {
        if (!List.of("Lax", "Strict", "None").contains(sameSite) || !List.of("auto", "true", "false").contains(secure))
            throw new IllegalArgumentException("Invalid refresh cookie configuration");
        if (sameSite.equals("None") && !secure.equals("true")) throw new IllegalArgumentException("SameSite None requires Secure=true");
        this.sameSite = sameSite; this.secure = secure;
        this.clock = clock;
    }
    public String read(HttpServletRequest request) {
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) if (NAME.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
    public void set(HttpServletRequest request, HttpServletResponse response, IssuedSession session) {
        write(request, response, session.token(), Duration.between(clock.instant(), session.expiresAt()).getSeconds());
    }
    public void clear(HttpServletRequest request, HttpServletResponse response) { write(request, response, "", 0); }
    private void write(HttpServletRequest request, HttpServletResponse response, String value, long age) {
        boolean https = secure.equals("true") || (secure.equals("auto") && request.isSecure());
        if (request.isSecure() && !https) throw new IllegalStateException("HTTPS refresh cookies require Secure");
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NAME, value).httpOnly(true).secure(https)
                .sameSite(sameSite).path("/api/auth").maxAge(Math.max(0, age)).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}

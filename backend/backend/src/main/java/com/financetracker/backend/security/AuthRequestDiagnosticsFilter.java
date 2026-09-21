package com.financetracker.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/** Temporary request metadata logging for diagnosing production auth failures. */
public class AuthRequestDiagnosticsFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthRequestDiagnosticsFilter.class);
    private static final int MAX_LOG_VALUE_LENGTH = 512;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String method = safeLogValue(request.getMethod());
        String requestUri = safeLogValue(request.getRequestURI());
        String serverName = safeLogValue(request.getServerName());
        String origin = safeLogValue(request.getHeader("Origin"));
        String host = safeLogValue(request.getHeader("Host"));
        String forwardedHost = safeLogValue(request.getHeader("X-Forwarded-Host"));
        String forwardedProto = safeLogValue(request.getHeader("X-Forwarded-Proto"));
        String forwarded = safeLogValue(request.getHeader("Forwarded"));

        try {
            filterChain.doFilter(request, response);
        } finally {
            LOGGER.info(
                    "TEMP auth request diagnostic method={} uri={} serverName={} origin={} host={} "
                            + "xForwardedHost={} xForwardedProto={} forwarded={} responseStatus={}",
                    method,
                    requestUri,
                    serverName,
                    origin,
                    host,
                    forwardedHost,
                    forwardedProto,
                    forwarded,
                    response.getStatus()
            );
        }
    }

    private String safeLogValue(String value) {
        if (value == null) {
            return "<absent>";
        }

        String sanitized = value.replaceAll("[\\p{Cntrl}]", "?");
        if (sanitized.length() <= MAX_LOG_VALUE_LENGTH) {
            return sanitized;
        }

        return sanitized.substring(0, MAX_LOG_VALUE_LENGTH) + "...";
    }
}

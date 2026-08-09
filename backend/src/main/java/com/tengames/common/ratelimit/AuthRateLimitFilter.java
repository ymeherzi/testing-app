package com.tengames.common.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

/**
 * Caps how hard one client may hammer the auth endpoints.
 *
 * <p>The per-address limit in {@code VerificationService} already stops us
 * being used to mail a stranger repeatedly. This is the other half: it slows
 * an attacker walking through many addresses or many passwords from a single
 * source. The window is generous, because a household or an office shares one
 * address and a family playing together must never trip it.
 */
@Component
@Order(1)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int REQUESTS_PER_IP = 60;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final RateLimiter rateLimiter;

    public AuthRateLimitFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only the credential-handling endpoints; /options is a harmless read.
        return !request.getRequestURI().startsWith("/api/auth/")
                || request.getRequestURI().equals("/api/auth/options");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            // Real client address: forward-headers-strategy=framework already
            // resolves X-Forwarded-For, so this is not the proxy's own address.
            rateLimiter.check("auth-ip:" + request.getRemoteAddr(), REQUESTS_PER_IP, WINDOW,
                    "Too many sign-in attempts — try again later");
        } catch (ResponseStatusException e) {
            response.sendError(e.getStatusCode().value(), e.getReason());
            return;
        }
        chain.doFilter(request, response);
    }
}

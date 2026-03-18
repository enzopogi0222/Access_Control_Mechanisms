package com.test.ias_firebase.config;

import com.test.ias_firebase.service.MfaAuditService;
import com.test.ias_firebase.service.TotpService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Enforces MFA (TOTP enrollment).
 *
 * Current policy:
 * - Any authenticated user must be TOTP-enrolled to access any authenticated route,
 *   except the TOTP enrollment endpoints/pages (so users can enroll).
 */
@Component
public class MfaEnforcementFilter extends OncePerRequestFilter {
    private final TotpService totpService;
    private final MfaAuditService mfaAuditService;

    public MfaEnforcementFilter(TotpService totpService, MfaAuditService mfaAuditService) {
        this.totpService = totpService;
        this.mfaAuditService = mfaAuditService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) return true;

        // Always allow unauthenticated/static paths.
        if (path.equals("/") || path.equals("/index")) return true;
        if (path.startsWith("/css/") || path.startsWith("/js/") || path.equals("/favicon.ico")) return true;

        // Let users reach the MFA enrollment flows even when MFA is mandatory.
        if (path.equals("/setup-totp")) return true;
        if (path.startsWith("/api/totp/")) return true;

        // Allow login endpoints; MFA enforcement happens after authentication exists.
        if (path.equals("/api/sessionLogin") || path.equals("/api/verifyTotp")) return true;

        // Enforce on everything else (but only for authenticated + not-enrolled users).
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String uid = authentication.getName();
        if (uid == null || uid.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (totpService.isEnrolled(uid)) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        mfaAuditService.record(
                request,
                MfaAuditService.EventType.MFA_POLICY_DENY,
                uid,
                "Access blocked: MFA enrollment required by policy",
                Map.of("path", path, "method", request.getMethod())
        );

        // If the client expects HTML, redirect to enrollment page.
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.TEXT_HTML_VALUE)) {
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.setHeader("Location", "/setup-totp");
            return;
        }

        // Otherwise return JSON 403.
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"error":"mfa_required","message":"MFA enrollment is required to access this resource.","redirect":"/setup-totp"}
                """.trim());
    }
}


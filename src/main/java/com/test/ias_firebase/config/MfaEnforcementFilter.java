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
 * Enforces MFA (TOTP enrollment) for sensitive routes.
 *
 * Current policy:
 * - Any authenticated user must be TOTP-enrolled to access /api/admin/** and /register
 * - Enrollment endpoints remain accessible so users can enroll ( /setup-totp, /api/totp/** )
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

        // Let users reach enrollment flows even if MFA is required elsewhere.
        if (path.equals("/setup-totp")) return true;
        if (path.startsWith("/api/totp/")) return true;

        // Only enforce on specific sensitive routes.
        return !(path.startsWith("/api/admin/") || path.equals("/register"));
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


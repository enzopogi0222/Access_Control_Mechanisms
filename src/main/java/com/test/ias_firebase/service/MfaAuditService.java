package com.test.ias_firebase.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Logs MFA (TOTP) security events to application logs and keeps a small in-memory buffer
 * for quick inspection in demos/tests.
 */
@Service
public class MfaAuditService {
    private static final Logger log = LoggerFactory.getLogger(MfaAuditService.class);

    public enum EventType {
        MFA_CHALLENGE_REQUIRED,
        MFA_VERIFY_SUCCESS,
        MFA_VERIFY_FAILURE,
        MFA_ENROLL_SUCCESS,
        MFA_ENROLL_FAILURE,
        MFA_POLICY_DENY
    }

    public record MfaEvent(
            Instant at,
            EventType type,
            String uid,
            String ip,
            String userAgent,
            String reason,
            Map<String, Object> details
    ) {}

    private static final int MAX_BUFFER = 500;
    private final List<MfaEvent> buffer = Collections.synchronizedList(new ArrayList<>());

    public void record(HttpServletRequest request,
                       EventType type,
                       String uid,
                       String reason,
                       Map<String, Object> details) {
        String ip = clientIp(request);
        String ua = request != null ? request.getHeader("User-Agent") : null;
        MfaEvent event = new MfaEvent(
                Instant.now(),
                type,
                uid != null ? uid : "-",
                ip != null ? ip : "-",
                ua != null ? ua : "-",
                reason != null ? reason : "-",
                details != null ? details : Map.of()
        );

        buffer.add(event);
        trimBuffer();

        // Keep this log line stable and grep-friendly for "Analyze logs".
        log.info("MFA type={} uid={} ip={} reason={} details={}",
                event.type(),
                event.uid(),
                event.ip(),
                event.reason(),
                event.details()
        );
    }

    public void record(HttpServletRequest request,
                       EventType type,
                       String uid,
                       String reason) {
        record(request, type, uid, reason, Map.of());
    }

    public List<MfaEvent> recent(int limit) {
        int cap = Math.max(1, Math.min(limit, 500));
        List<MfaEvent> snapshot;
        synchronized (buffer) {
            snapshot = List.copyOf(buffer);
        }
        int from = Math.max(0, snapshot.size() - cap);
        return snapshot.subList(from, snapshot.size());
    }

    private void trimBuffer() {
        synchronized (buffer) {
            int extra = buffer.size() - MAX_BUFFER;
            if (extra <= 0) return;
            buffer.subList(0, extra).clear();
        }
    }

    private static String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        // If behind a proxy, your infra may set X-Forwarded-For.
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // first IP in list is the original client IP
            int comma = xff.indexOf(',');
            return (comma >= 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}


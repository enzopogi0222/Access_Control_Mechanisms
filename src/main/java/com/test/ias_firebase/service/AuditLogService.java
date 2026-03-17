package com.test.ias_firebase.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Simple audit logger for access control decisions (allow/deny).
 * Keeps an in-memory buffer for demo and logs to application logs.
 */
@Service
public class AuditLogService {
    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    public record AuditEvent(
            Instant at,
            String uid,
            String action,
            String resourceType,
            String resourceId,
            String resourceClassification,
            String userClearance,
            boolean allowed,
            String reason
    ) {}

    private final List<AuditEvent> buffer = Collections.synchronizedList(new ArrayList<>());

    public void record(AuditEvent event) {
        if (event == null) return;
        buffer.add(event);
        log.info("AUDIT action={} uid={} resource={}#{} allowed={} reason={} classification={} clearance={}",
                event.action(),
                event.uid(),
                event.resourceType(),
                event.resourceId(),
                event.allowed(),
                event.reason(),
                event.resourceClassification(),
                event.userClearance()
        );
    }

    public void record(String uid,
                       String action,
                       String resourceType,
                       String resourceId,
                       String classification,
                       String clearance,
                       boolean allowed,
                       String reason) {
        record(new AuditEvent(
                Instant.now(),
                uid,
                action,
                resourceType,
                resourceId,
                classification != null ? classification : "-",
                clearance != null ? clearance : "-",
                allowed,
                reason
        ));
    }

    /** For demo screenshots: returns recent events (not persisted). */
    public List<Map<String, Object>> recent(int limit) {
        int cap = Math.max(1, Math.min(limit, 200));
        List<AuditEvent> snapshot;
        synchronized (buffer) {
            snapshot = List.copyOf(buffer);
        }
        int from = Math.max(0, snapshot.size() - cap);
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = snapshot.size() - 1; i >= from; i--) {
            AuditEvent e = snapshot.get(i);
            out.add(Map.of(
                    "at", e.at().toString(),
                    "uid", e.uid(),
                    "action", e.action(),
                    "resourceType", e.resourceType(),
                    "resourceId", e.resourceId(),
                    "classification", e.resourceClassification() != null ? e.resourceClassification() : "-",
                    "clearance", e.userClearance() != null ? e.userClearance() : "-",
                    "allowed", e.allowed(),
                    "reason", e.reason()
            ));
        }
        return out;
    }
}


package com.test.ias_firebase.service;

import com.test.ias_firebase.model.FileResource;
import com.test.ias_firebase.model.SecurityLevel;
import org.springframework.stereotype.Service;

/**
 * Web-app MAC policy: enforces mandatory clearance/classification checks.
 *
 * This is intentionally centralized so every endpoint uses the same logic.
 */
@Service
public class MacPolicyService {

    private final UserClearanceService userClearanceService;
    private final AuditLogService auditLogService;

    public MacPolicyService(UserClearanceService userClearanceService,
                            AuditLogService auditLogService) {
        this.userClearanceService = userClearanceService;
        this.auditLogService = auditLogService;
    }

    public SecurityLevel clearanceOf(String uid) {
        return userClearanceService.getClearance(uid);
    }

    public void enforceCanReadFile(String uid, FileResource file) {
        SecurityLevel clearance = clearanceOf(uid);
        SecurityLevel classification = file != null ? file.getClassification() : null;

        boolean allowed = clearance != null && clearance.atLeast(classification);
        if (!allowed) {
            auditLogService.record(uid, "READ", "FileResource", safeId(file), classification, clearance, false, "clearance_too_low");
            throw forbidden("MAC: clearance too low for this resource");
        }
        auditLogService.record(uid, "READ", "FileResource", safeId(file), classification, clearance, true, "ok");
    }

    public void enforceCanCreateFileWithLabel(String uid, SecurityLevel classification) {
        SecurityLevel clearance = clearanceOf(uid);
        SecurityLevel c = (classification != null) ? classification : SecurityLevel.PUBLIC;
        boolean allowed = clearance != null && clearance.atLeast(c);
        if (!allowed) {
            auditLogService.record(uid, "CREATE", "FileResource", "-", c, clearance, false, "classification_above_clearance");
            throw forbidden("MAC: cannot create resource above your clearance");
        }
        auditLogService.record(uid, "CREATE", "FileResource", "-", c, clearance, true, "ok");
    }

    public void enforceCanRelabel(String uid, boolean isAdmin, FileResource file, SecurityLevel newClassification) {
        SecurityLevel clearance = clearanceOf(uid);
        SecurityLevel requested = (newClassification != null) ? newClassification : SecurityLevel.PUBLIC;

        if (!isAdmin) {
            auditLogService.record(uid, "RELABEL", "FileResource", safeId(file), requested, clearance, false, "admin_required");
            throw forbidden("MAC: only admin can change classification");
        }

        // Optional hardening: admin still can't label above their own clearance.
        boolean allowed = clearance != null && clearance.atLeast(requested);
        if (!allowed) {
            auditLogService.record(uid, "RELABEL", "FileResource", safeId(file), requested, clearance, false, "admin_clearance_too_low");
            throw forbidden("MAC: admin clearance too low for requested label");
        }

        auditLogService.record(uid, "RELABEL", "FileResource", safeId(file), requested, clearance, true, "ok");
    }

    private static RuntimeException forbidden(String message) {
        return new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                message
        );
    }

    private static String safeId(FileResource file) {
        if (file == null || file.getId() == null) return "-";
        return file.getId();
    }
}


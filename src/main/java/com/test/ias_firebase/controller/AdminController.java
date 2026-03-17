package com.test.ias_firebase.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.test.ias_firebase.model.Role;
import com.test.ias_firebase.model.SecurityLevel;
import com.test.ias_firebase.service.AuditLogService;
import com.test.ias_firebase.service.UserClearanceService;
import com.test.ias_firebase.service.UserRoleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin-only endpoints (RBAC: requires ROLE_ADMIN).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRoleService userRoleService;
    private final UserClearanceService userClearanceService;
    private final AuditLogService auditLogService;

    public AdminController(UserRoleService userRoleService,
                           UserClearanceService userClearanceService,
                           AuditLogService auditLogService) {
        this.userRoleService = userRoleService;
        this.userClearanceService = userClearanceService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/roles")
    public Map<String, String> listRoles() {
        return userRoleService.getAllRoles();
    }

    @PostMapping("/roles")
    public ResponseEntity<?> setRole(Authentication authentication,
                                     @RequestBody Map<String, String> body) {
        String uid = body.get("uid");
        String roleStr = body.get("role");
        if (uid == null || roleStr == null) {
            return ResponseEntity.badRequest().build();
        }
        Role role;
        try {
            role = Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role: " + roleStr));
        }
        userRoleService.setRole(uid, role);
        return ResponseEntity.ok(Map.of("uid", uid, "role", role.name()));
    }

    // --- MAC admin endpoints ---

    @GetMapping("/clearance")
    public Map<String, String> listClearances() {
        return userClearanceService.getAllClearances();
    }

    /**
     * Body: { "uid": "<firebase uid OR email>", "clearance": "CONFIDENTIAL" }
     */
    @PostMapping("/clearance")
    public ResponseEntity<?> setClearance(@RequestBody Map<String, String> body) {
        String uidOrEmail = body.get("uid");
        String levelStr = body.get("clearance");
        if (uidOrEmail == null || levelStr == null) {
            return ResponseEntity.badRequest().build();
        }
        SecurityLevel level = SecurityLevel.parseOrDefault(levelStr, null);
        if (level == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid clearance: " + levelStr));
        }
        ResolvedUser resolved = resolveUidIfEmail(uidOrEmail);
        if (resolved.email() != null) userClearanceService.setClearanceByEmail(resolved.email(), resolved.uid(), level);
        else userClearanceService.setClearance(resolved.uid(), level);
        return ResponseEntity.ok(Map.of("uid", resolved.uid(), "clearance", level.name()));
    }

    private record ResolvedUser(String uid, String email) {}

    private static ResolvedUser resolveUidIfEmail(String uidOrEmail) {
        String value = uidOrEmail == null ? null : uidOrEmail.trim();
        if (value == null || value.isBlank()) return new ResolvedUser(value, null);
        if (!value.contains("@")) return new ResolvedUser(value, null);
        try {
            UserRecord record = FirebaseAuth.getInstance().getUserByEmail(value);
            return new ResolvedUser(record.getUid(), value);
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Unknown email (no Firebase user found)"
            );
        }
    }

    /**
     * Demo: view recent access control audit decisions.
     * Query param: limit (default 50, max 200)
     */
    @GetMapping("/audit")
    public ResponseEntity<?> recentAudit(@RequestParam(name = "limit", required = false) Integer limit) {
        int l = (limit == null) ? 50 : limit;
        return ResponseEntity.ok(auditLogService.recent(l));
    }
}

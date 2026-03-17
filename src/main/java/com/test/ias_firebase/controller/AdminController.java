package com.test.ias_firebase.controller;

import com.test.ias_firebase.model.Role;
import com.test.ias_firebase.service.AuditLogService;
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
    private final AuditLogService auditLogService;

    public AdminController(UserRoleService userRoleService,
                           AuditLogService auditLogService) {
        this.userRoleService = userRoleService;
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

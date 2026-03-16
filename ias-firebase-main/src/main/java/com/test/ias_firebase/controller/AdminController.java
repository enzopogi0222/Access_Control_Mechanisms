package com.test.ias_firebase.controller;

import com.test.ias_firebase.model.Role;
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

    public AdminController(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
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
}

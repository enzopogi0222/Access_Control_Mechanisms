package com.test.ias_firebase.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.ias_firebase.model.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RBAC: stores and resolves Firebase UID → Role.
 * Roles are persisted in a JSON file; default for unknown UIDs is USER (least privilege).
 */
@Service
public class UserRoleService {

    @Value("${app.roles.file:data/user-roles.json}")
    private String rolesFilePath;

    @Value("${app.roles.admin-uids:}")
    private List<String> bootstrapAdminUids;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> uidToRole = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadRolesFromFile() {
        try {
            Path path = Paths.get(rolesFilePath).toAbsolutePath();
            if (Files.exists(path)) {
                String json = Files.readString(path);
                Map<String, String> loaded = objectMapper.readValue(json, new TypeReference<>() {});
                if (loaded != null) uidToRole.putAll(loaded);
            }
        } catch (Exception e) {
            // File missing or invalid
        }
        if (bootstrapAdminUids != null) {
            for (String uid : bootstrapAdminUids) {
                if (uid != null && !uid.isBlank())
                    uidToRole.put(uid.trim(), Role.admin.name());
            }
            saveRolesToFile();
        }
    }

    private synchronized void saveRolesToFile() {
        try {
            Path path = Paths.get(rolesFilePath).toAbsolutePath();
            Files.createDirectories(path.getParent());
            String json = objectMapper.writeValueAsString(uidToRole);
            Files.writeString(path, json);
        } catch (Exception e) {
            // ignore
        }
    }

    public Role getRole(String uid) {
        if (uid == null || uid.isBlank()) return Role.user;
        String roleName = uidToRole.get(uid);
        if (roleName == null) return Role.user;
        try {
            return Role.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            return Role.user;
        }
    }

    /** Spring Security authority (e.g. ROLE_ADMIN, ROLE_USER). */
    public static String toAuthority(Role role) {
        return "ROLE_" + role.name().toUpperCase();
    }

    /** If no admins exist yet, make this user admin (first-login bootstrap). */
    public void ensureFirstUserIsAdmin(String uid) {
        if (uid == null || uid.isBlank()) return;
        if (uidToRole.isEmpty()) {
            setRole(uid, Role.admin);
        }
    }

    public void setRole(String uid, Role role) {
        if (uid == null || uid.isBlank()) return;
        uidToRole.put(uid, role.name());
        saveRolesToFile();
    }

    public Map<String, String> getAllRoles() {
        return Collections.unmodifiableMap(Map.copyOf(uidToRole));
    }
}

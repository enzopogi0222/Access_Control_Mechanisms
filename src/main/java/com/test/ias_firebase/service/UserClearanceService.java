package com.test.ias_firebase.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.ias_firebase.model.SecurityLevel;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web-app MAC: stores and resolves Firebase UID → Clearance (SecurityLevel).
 * Persisted in a JSON file; default for unknown UIDs is PUBLIC (least privilege).
 */
@Service
public class UserClearanceService {

    @Value("${app.clearance.file:data/user-clearance.json}")
    private String clearanceFilePath;

    /**
     * Optional bootstrap: set these UIDs to SECRET clearance on startup.
     * This is purely for lab/demo convenience.
     */
    @Value("${app.clearance.secret-uids:}")
    private List<String> bootstrapSecretUids;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> uidToClearance = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadFromFile() {
        try {
            Path path = Paths.get(clearanceFilePath).toAbsolutePath();
            if (Files.exists(path)) {
                String json = Files.readString(path);
                Map<String, String> loaded = objectMapper.readValue(json, new TypeReference<>() {});
                if (loaded != null) uidToClearance.putAll(loaded);
            }
        } catch (Exception e) {
            // ignore (missing/invalid file)
        }

        if (bootstrapSecretUids != null) {
            for (String uid : bootstrapSecretUids) {
                if (uid != null && !uid.isBlank()) {
                    uidToClearance.put(uid.trim(), SecurityLevel.SECRET.name());
                }
            }
            saveToFile();
        }
    }

    private synchronized void saveToFile() {
        try {
            Path path = Paths.get(clearanceFilePath).toAbsolutePath();
            Files.createDirectories(path.getParent());
            String json = objectMapper.writeValueAsString(uidToClearance);
            Files.writeString(path, json);
        } catch (Exception e) {
            // ignore
        }
    }

    public SecurityLevel getClearance(String uid) {
        if (uid == null || uid.isBlank()) return SecurityLevel.PUBLIC;
        String levelName = uidToClearance.get(uid);
        if (levelName == null) return SecurityLevel.PUBLIC;
        try {
            return SecurityLevel.valueOf(levelName);
        } catch (IllegalArgumentException e) {
            return SecurityLevel.PUBLIC;
        }
    }

    public void setClearance(String uid, SecurityLevel level) {
        if (uid == null || uid.isBlank()) return;
        SecurityLevel effective = (level != null) ? level : SecurityLevel.PUBLIC;
        uidToClearance.put(uid, effective.name());
        saveToFile();
    }

    public Map<String, String> getAllClearances() {
        return Collections.unmodifiableMap(Map.copyOf(uidToClearance));
    }
}


package com.test.ias_firebase.service;

import com.test.ias_firebase.model.SecurityLevel;
import com.test.ias_firebase.model.UserClearance;
import com.test.ias_firebase.repo.UserClearanceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Web-app MAC: stores and resolves Firebase UID → Clearance (SecurityLevel).
 * Persisted in SQL; default for unknown UIDs is PUBLIC (least privilege).
 */
@Service
public class UserClearanceService {

    /**
     * Optional bootstrap: set these UIDs to SECRET clearance on startup.
     * This is purely for lab/demo convenience.
     */
    @Value("${app.clearance.secret-uids:}")
    private List<String> bootstrapSecretUids;

    private final UserClearanceRepository userClearanceRepository;

    public UserClearanceService(UserClearanceRepository userClearanceRepository) {
        this.userClearanceRepository = userClearanceRepository;
    }

    public SecurityLevel getClearance(String uid) {
        if (uid == null || uid.isBlank()) return SecurityLevel.PUBLIC;
        return userClearanceRepository.findById(uid)
                .map(UserClearance::getClearance)
                .orElseGet(() -> isBootstrapSecret(uid) ? SecurityLevel.SECRET : SecurityLevel.PUBLIC);
    }

    public void setClearance(String uid, SecurityLevel level) {
        if (uid == null || uid.isBlank()) return;
        SecurityLevel effective = (level != null) ? level : SecurityLevel.PUBLIC;
        UserClearance row = userClearanceRepository.findById(uid).orElseGet(UserClearance::new);
        row.setUid(uid);
        row.setClearance(effective);
        userClearanceRepository.save(row);
    }

    public void setClearanceByEmail(String email, String uid, SecurityLevel level) {
        if (uid == null || uid.isBlank()) return;
        SecurityLevel effective = (level != null) ? level : SecurityLevel.PUBLIC;
        UserClearance row = userClearanceRepository.findById(uid).orElseGet(UserClearance::new);
        row.setUid(uid);
        row.setEmail(email);
        row.setClearance(effective);
        userClearanceRepository.save(row);
    }

    public Map<String, String> getAllClearances() {
        return userClearanceRepository.findAll()
                .stream()
                .collect(Collectors.toUnmodifiableMap(UserClearance::getUid, uc -> uc.getClearance().name()));
    }

    private boolean isBootstrapSecret(String uid) {
        if (bootstrapSecretUids == null) return false;
        return bootstrapSecretUids.stream().anyMatch(x -> uid.equals(x != null ? x.trim() : null));
    }
}


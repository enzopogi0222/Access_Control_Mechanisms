package com.test.ias_firebase.controller;

import com.test.ias_firebase.service.UserClearanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Convenience endpoints for MAC demo screenshots.
 */
@RestController
@RequestMapping("/api/mac")
public class MacController {

    private final UserClearanceService userClearanceService;

    public MacController(UserClearanceService userClearanceService) {
        this.userClearanceService = userClearanceService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        String uid = authentication.getName();
        return ResponseEntity.ok(Map.of(
                "uid", uid,
                "clearance", userClearanceService.getClearance(uid).name()
        ));
    }
}


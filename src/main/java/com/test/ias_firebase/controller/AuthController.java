package com.test.ias_firebase.controller;

import java.util.List;
import java.util.Map;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.test.ias_firebase.model.Role;
import com.test.ias_firebase.service.TotpService;
import com.test.ias_firebase.service.UserRoleService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;



@RestController
@RequestMapping("/api")
public class AuthController {

    private final SecurityContextRepository securityContextRepository;
    private final TotpService totpService;
    private final UserRoleService userRoleService;

    public AuthController(SecurityContextRepository securityContextRepository,
                          TotpService totpService,
                          UserRoleService userRoleService) {
        this.securityContextRepository = securityContextRepository;
        this.totpService = totpService;
        this.userRoleService = userRoleService;
    }

    @PostMapping("/sessionLogin")
    public ResponseEntity<?> sessionLogin(@RequestBody Map<String, String> body,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {

        String idToken = body.get("idToken");

        try {
            FirebaseToken decodedToken =
                    FirebaseAuth.getInstance().verifyIdToken(idToken);
            String uid = decodedToken.getUid();

            if (totpService.isEnrolled(uid)) {
                String tempToken = totpService.createTempToken(uid);
                return ResponseEntity.ok(Map.of(
                    "totpRequired", true,
                    "tempToken", tempToken
                ));
            }

            userRoleService.ensureFirstUserIsAdmin(uid);
            Role role = userRoleService.getRole(uid);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            uid,
                            null,
                            List.of(new SimpleGrantedAuthority(UserRoleService.toAuthority(role)))
                    );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            securityContextRepository.saveContext(context, request, response);

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/verifyTotp")
    public ResponseEntity<?> verifyTotp(@RequestBody Map<String, String> body,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        String tempToken = body.get("tempToken");
        String codeStr = body.get("code");
        if (tempToken == null || codeStr == null || codeStr.length() != 6) {
            return ResponseEntity.badRequest().build();
        }
        int code;
        try {
            code = Integer.parseInt(codeStr);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }

        String uid = totpService.verifyAndConsumeTempToken(tempToken, code);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        userRoleService.ensureFirstUserIsAdmin(uid);
        Role role = userRoleService.getRole(uid);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        uid,
                        null,
                        List.of(new SimpleGrantedAuthority(UserRoleService.toAuthority(role)))
                );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/totp/setup")
    public ResponseEntity<?> totpSetup(Authentication authentication,
                                       @RequestParam(required = false) String email) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String uid = authentication.getName();
        String accountName = (email != null && !email.isBlank()) ? email : uid;
        TotpService.SetupResult result = totpService.setup(uid, accountName);
        return ResponseEntity.ok(Map.of(
            "secret", result.secret(),
            "otpAuthUrl", result.otpAuthUrl(),
            "qrImageUrl", result.qrImageUrl()
        ));
    }

    @PostMapping("/totp/confirm")
    public ResponseEntity<?> totpConfirm(Authentication authentication,
                                         @RequestBody Map<String, String> body) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String uid = authentication.getName();
        String codeStr = body.get("code");
        if (codeStr == null || codeStr.length() != 6) {
            return ResponseEntity.badRequest().build();
        }
        int code;
        try {
            code = Integer.parseInt(codeStr);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }
        if (!totpService.confirmSetup(uid, code)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid code"));
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/secure")
    public String secure(Authentication authentication) {
        return "Logged in as UID: " + authentication.getName();
    }


    
}

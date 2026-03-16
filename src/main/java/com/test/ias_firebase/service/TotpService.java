package com.test.ias_firebase.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TOTP (Google Authenticator) service: generate secret, QR URL, and verify code.
 * Enrolled secrets are persisted to a file so they survive server restart.
 */
@Service
public class TotpService {

    private static final String ISSUER = "IAS Firebase";

    @Value("${app.totp.secrets-file:data/totp-secrets.json}")
    private String secretsFilePath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** TOTP code changes every 30 seconds by default (library: com.warrenstrange.googleauth).
     *  To override: GoogleAuthenticatorConfig cfg = new GoogleAuthenticatorConfig();
     *  cfg.setTimeStepSizeInMillis(TimeUnit.SECONDS.toMillis(30)); // default 30 sec
     *  new GoogleAuthenticator(cfg); */
    private final GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();

    /** UID -> TOTP secret (enrolled users only, after confirm). Loaded from file on startup; saved on confirm. */
    private final Map<String, String> totpSecrets = new ConcurrentHashMap<>();

    /** UID -> TOTP secret (pending, not yet confirmed in setup). In-memory only. */
    private final Map<String, String> totpPending = new ConcurrentHashMap<>();

    /** Temp token -> UID (for TOTP step after email/password). Expiry handled by cleanup. */
    private final Map<String, PendingTotpLogin> tempTokens = new ConcurrentHashMap<>();

    private static final long TEMP_TOKEN_TTL_MS = 5 * 60 * 1000; // 5 minutes

    @PostConstruct
    public void loadSecretsFromFile() {
        try {
            Path path = Paths.get(secretsFilePath).toAbsolutePath();
            if (Files.exists(path)) {
                String json = Files.readString(path);
                Map<String, String> loaded = objectMapper.readValue(json, new TypeReference<>() {});
                if (loaded != null) totpSecrets.putAll(loaded);
            }
        } catch (Exception e) {
            // File missing or invalid: start with empty map
        }
    }

    private synchronized void saveSecretsToFile() {
        try {
            Path path = Paths.get(secretsFilePath).toAbsolutePath();
            Files.createDirectories(path.getParent());
            String json = objectMapper.writeValueAsString(totpSecrets);
            Files.writeString(path, json);
        } catch (Exception e) {
            // Log and continue; enrollment still in memory
        }
    }

    public static class PendingTotpLogin {
        public final String uid;
        public final long expiresAt;

        public PendingTotpLogin(String uid) {
            this.uid = uid;
            this.expiresAt = System.currentTimeMillis() + TEMP_TOKEN_TTL_MS;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public boolean isEnrolled(String uid) {
        return totpSecrets.containsKey(uid);
    }

    /** Generate TOTP secret and QR URL for setup. Caller must be authenticated (uid). */
    public SetupResult setup(String uid, String accountName) {
        GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
        String secret = key.getKey();
        totpPending.put(uid, secret);

        String otpAuthUrl = GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(ISSUER, accountName, key);
        String qrImageUrl = GoogleAuthenticatorQRGenerator.getOtpAuthURL(ISSUER, accountName, key);

        return new SetupResult(secret, otpAuthUrl, qrImageUrl);
    }

    public record SetupResult(String secret, String otpAuthUrl, String qrImageUrl) {}

    /** Confirm setup by verifying one TOTP code; then mark user as enrolled. */
    public boolean confirmSetup(String uid, int code) {
        String secret = totpPending.get(uid);
        if (secret == null) return false;
        if (!googleAuthenticator.authorize(secret, code)) return false;
        totpSecrets.put(uid, secret);
        totpPending.remove(uid);
        saveSecretsToFile();
        return true;
    }

    /** Create temp token when login requires TOTP (do not create session yet). */
    public String createTempToken(String uid) {
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        tempTokens.put(token, new PendingTotpLogin(uid));
        return token;
    }

    /** Verify TOTP code using temp token; returns uid if valid, null otherwise. */
    public String verifyAndConsumeTempToken(String tempToken, int code) {
        PendingTotpLogin pending = tempTokens.get(tempToken);
        if (pending == null || pending.isExpired()) {
            tempTokens.remove(tempToken);
            return null;
        }
        String uid = pending.uid;
        String secret = totpSecrets.get(uid);
        if (secret == null || !googleAuthenticator.authorize(secret, code)) {
            return null;
        }
        tempTokens.remove(tempToken);
        return uid;
    }

    /** Verify TOTP code for an enrolled user (e.g. during login with temp token). */
    public boolean verifyCode(String uid, int code) {
        String secret = totpSecrets.get(uid);
        return secret != null && googleAuthenticator.authorize(secret, code);
    }
}

package com.test.ias_firebase.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.test.ias_firebase.model.FileResource;
import com.test.ias_firebase.model.SecurityLevel;
import com.test.ias_firebase.service.FileResourceService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Web DAC controller: exposes /api/files and enforces DAC using the FileResource model.
 * Owner is set on create; read is allowed only for owner or users in allowedUsers (via share).
 */
@RestController
@RequestMapping("/api/files")
public class FileResourceController {
    private final FileResourceService fileResourceService;

    public FileResourceController(FileResourceService fileResourceService) {
        this.fileResourceService = fileResourceService;
    }

    /**
     * DAC Step 2: Save Owner (current authenticated user becomes owner).
     *
     * Body: { "filename": "report.pdf" }
     */
    @PostMapping
    public ResponseEntity<FileResource> create(Authentication authentication,
                                               @RequestBody Map<String, String> body) {
        String user = authentication != null ? authentication.getName() : null;
        String filename = body != null ? body.get("filename") : null;
        SecurityLevel classification = SecurityLevel.parseOrDefault(body != null ? body.get("classification") : null, SecurityLevel.PUBLIC);
        FileResource created = fileResourceService.create(filename, user, classification);
        return ResponseEntity.ok(created);
    }

    /**
     * Upload an actual file to local storage (multipart/form-data).
     *
     * Form field: file=<binary>
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileResource> upload(Authentication authentication,
                                               @RequestPart("file") MultipartFile file,
                                               @RequestParam(name = "classification", required = false) String classification) {
        String user = authentication != null ? authentication.getName() : null;
        SecurityLevel level = SecurityLevel.parseOrDefault(classification, SecurityLevel.PUBLIC);
        return ResponseEntity.ok(fileResourceService.upload(file, user, level));
    }

    /**
     * DAC Step 3: Enforce DAC (only owner or allowedUsers may read).
     */
    @GetMapping("/{id}")
    public ResponseEntity<FileResource> get(Authentication authentication, @PathVariable String id) {
        String user = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(fileResourceService.getForRead(id, user));
    }

    /**
     * Download the actual file bytes (streams from local disk).
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(Authentication authentication, @PathVariable String id) {
        String user = authentication != null ? authentication.getName() : null;
        FileResource meta = fileResourceService.getForRead(id, user);
        Path path = fileResourceService.getPathForDownload(id, user);

        Resource resource;
        try {
            resource = new UrlResource(path.toUri());
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Invalid file path");
        }

        MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
        if (meta.getContentType() != null && !meta.getContentType().isBlank()) {
            try {
                contentType = MediaType.parseMediaType(meta.getContentType());
            } catch (Exception ignored) {
                // fall back to octet-stream
            }
        }

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeHeaderFilename(meta.getFilename()) + "\"")
                .body(resource);
    }

    /**
     * Convenience endpoint for demo screenshots: list files you own.
     */
    @GetMapping
    public ResponseEntity<List<FileResource>> listMine(Authentication authentication) {
        String user = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(fileResourceService.listOwnedBy(user));
    }

    /**
     * List files that were shared with the current user (caller is in allowedUsers).
     */
    @GetMapping("/shared-with-me")
    public ResponseEntity<List<FileResource>> listSharedWithMe(Authentication authentication) {
        String user = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(fileResourceService.listSharedWith(user));
    }

    /**
     * Optional ACL-like feature: owner can share access to another user.
     *
     * Body: { "allowedUser": "<uid-or-email>" }
     */
    @PostMapping("/{id}/share")
    public ResponseEntity<FileResource> share(Authentication authentication,
                                              @PathVariable String id,
                                              @RequestBody Map<String, String> body) {
        String user = authentication != null ? authentication.getName() : null;
        String allowedUser = body != null ? body.get("allowedUser") : null;
        String allowedUid = resolveUidIfEmail(allowedUser);
        return ResponseEntity.ok(fileResourceService.shareWithUser(id, user, allowedUid));
    }

    /**
     * MAC: admin-only relabel (classification).
     *
     * Body: { "classification": "CONFIDENTIAL" }
     */
    @PostMapping("/{id}/label")
    public ResponseEntity<FileResource> relabel(Authentication authentication,
                                                @PathVariable String id,
                                                @RequestBody Map<String, String> body) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        String uid = authentication.getName();
        boolean isAdmin = authentication.getAuthorities() != null &&
                authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        SecurityLevel newLevel = SecurityLevel.parseOrDefault(body != null ? body.get("classification") : null, SecurityLevel.PUBLIC);
        return ResponseEntity.ok(fileResourceService.relabel(id, uid, isAdmin, newLevel));
    }

    private static String resolveUidIfEmail(String allowedUser) {
        if (allowedUser == null) return null;
        String value = allowedUser.trim();
        if (value.isBlank()) return value;

        // Allow either UID or email. If it looks like an email, resolve to UID.
        if (value.contains("@")) {
            try {
                UserRecord record = FirebaseAuth.getInstance().getUserByEmail(value);
                return record.getUid();
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown email (no Firebase user found)");
            }
        }
        return value;
    }

    private static String safeHeaderFilename(String name) {
        if (name == null || name.isBlank()) return "file";
        return name.replace("\"", "");
    }
}


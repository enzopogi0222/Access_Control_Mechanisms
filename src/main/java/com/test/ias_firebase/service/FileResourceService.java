package com.test.ias_firebase.service;

import com.test.ias_firebase.model.FileResource;
import com.test.ias_firebase.model.SecurityLevel;
import com.test.ias_firebase.repo.FileResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;

@Service
public class FileResourceService {
    private final Path storageDir;
    private final MacPolicyService macPolicyService;
    private final FileResourceRepository fileResourceRepository;

    public FileResourceService(@Value("${app.files.storage-dir:uploads}") String storageDir,
                               MacPolicyService macPolicyService,
                               FileResourceRepository fileResourceRepository) {
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
        this.macPolicyService = macPolicyService;
        this.fileResourceRepository = fileResourceRepository;
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create storage dir: " + this.storageDir, e);
        }
    }

    public FileResource create(String filename, String ownerIdentifier, SecurityLevel classification) {
        if (filename == null || filename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filename is required");
        }
        if (ownerIdentifier == null || ownerIdentifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        SecurityLevel effective = (classification != null) ? classification : SecurityLevel.PUBLIC;
        macPolicyService.enforceCanCreateFileWithLabel(ownerIdentifier, effective);

        FileResource file = new FileResource();
        file.setId(UUID.randomUUID().toString());
        file.setFilename(filename);
        file.setOwnerEmail(ownerIdentifier);
        file.setClassification(effective);
        return fileResourceRepository.save(file);
    }

    public FileResource upload(MultipartFile multipartFile, String ownerIdentifier, SecurityLevel classification) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        if (ownerIdentifier == null || ownerIdentifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        SecurityLevel effective = (classification != null) ? classification : SecurityLevel.PUBLIC;
        macPolicyService.enforceCanCreateFileWithLabel(ownerIdentifier, effective);

        String originalName = sanitizeOriginalFilename(multipartFile.getOriginalFilename());
        if (originalName == null || originalName.isBlank()) {
            originalName = "file";
        }

        String storedFilename = UUID.randomUUID() + guessExtension(originalName);
        Path target = storageDir.resolve(storedFilename).normalize();
        if (!target.startsWith(storageDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename");
        }

        try (InputStream in = multipartFile.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }

        FileResource file = new FileResource();
        file.setId(UUID.randomUUID().toString());
        file.setFilename(originalName);
        file.setStoredFilename(storedFilename);
        file.setContentType(multipartFile.getContentType());
        file.setSizeBytes(multipartFile.getSize());
        file.setUploadedAt(Instant.now());
        file.setOwnerEmail(ownerIdentifier);
        file.setClassification(effective);
        return fileResourceRepository.save(file);
    }

    public FileResource getForRead(String id, String userIdentifier) {
        FileResource file = getById(id);
        enforceDac(file, userIdentifier);
        macPolicyService.enforceCanReadFile(userIdentifier, file);
        return file;
    }

    public Path getPathForDownload(String id, String userIdentifier) {
        FileResource file = getById(id);
        enforceDac(file, userIdentifier);
        macPolicyService.enforceCanReadFile(userIdentifier, file);
        if (file.getStoredFilename() == null || file.getStoredFilename().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File content not found");
        }
        Path p = storageDir.resolve(file.getStoredFilename()).normalize();
        if (!p.startsWith(storageDir) || !Files.isRegularFile(p)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File content not found");
        }
        return p;
    }

    public List<FileResource> listOwnedBy(String ownerIdentifier) {
        if (ownerIdentifier == null || ownerIdentifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        List<FileResource> out = new ArrayList<>();
        for (FileResource f : fileResourceRepository.findByOwnerEmail(ownerIdentifier)) {
            if (macPolicyService.clearanceOf(ownerIdentifier).atLeast(f.getClassification())) out.add(f);
        }
        out.sort(Comparator.comparing(FileResource::getFilename, Comparator.nullsLast(String::compareToIgnoreCase)));
        return out;
    }

    /** List files where caller is in allowedUsers (i.e., shared with caller). */
    public List<FileResource> listSharedWith(String userIdentifier) {
        if (userIdentifier == null || userIdentifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        List<FileResource> out = new ArrayList<>();
        for (FileResource f : fileResourceRepository.findSharedWith(userIdentifier)) {
            if (macPolicyService.clearanceOf(userIdentifier).atLeast(f.getClassification())) out.add(f);
        }
        out.sort(Comparator.comparing(FileResource::getFilename, Comparator.nullsLast(String::compareToIgnoreCase)));
        return out;
    }

    public FileResource shareWithUser(String id, String ownerIdentifier, String allowedUserIdentifier) {
        if (allowedUserIdentifier == null || allowedUserIdentifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "allowedUser is required");
        }
        FileResource file = getById(id);
        if (!Objects.equals(file.getOwnerEmail(), ownerIdentifier)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }
        if (!file.getAllowedUsers().contains(allowedUserIdentifier)) {
            file.getAllowedUsers().add(allowedUserIdentifier);
        }
        return fileResourceRepository.save(file);
    }

    public FileResource relabel(String id, String uid, boolean isAdmin, SecurityLevel newClassification) {
        FileResource file = getById(id);
        macPolicyService.enforceCanRelabel(uid, isAdmin, file, newClassification);
        file.setClassification(newClassification);
        return fileResourceRepository.save(file);
    }

    private FileResource getById(String id) {
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
        }
        return fileResourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
    }

    private void enforceDac(FileResource file, String userIdentifier) {
        if (userIdentifier == null || userIdentifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        boolean isOwner = Objects.equals(file.getOwnerEmail(), userIdentifier);
        boolean isAllowed = file.getAllowedUsers() != null && file.getAllowedUsers().contains(userIdentifier);
        if (!isOwner && !isAllowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }
    }

    private static String sanitizeOriginalFilename(String originalFilename) {
        if (originalFilename == null) return null;
        String name = originalFilename.replace("\\", "/");
        int idx = name.lastIndexOf('/');
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        name = name.trim();
        if (name.equals(".") || name.equals("..")) return null;
        return name;
    }

    private static String guessExtension(String originalName) {
        int dot = originalName.lastIndexOf('.');
        if (dot <= 0 || dot == originalName.length() - 1) return "";
        String ext = originalName.substring(dot).toLowerCase(Locale.ROOT);
        if (ext.length() > 10) return "";
        if (!ext.matches("\\.[a-z0-9]+")) return "";
        return ext;
    }
}


package com.test.ias_firebase.service;

import com.test.ias_firebase.model.FileResource;
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
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileResourceService {
    private final Map<String, FileResource> store = new ConcurrentHashMap<>();
    private final Path storageDir;

    public FileResourceService(@Value("${app.files.storage-dir:uploads}") String storageDir) {
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create storage dir: " + this.storageDir, e);
        }
    }

    public FileResource create(String filename, String ownerIdentifier) {
        if (filename == null || filename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filename is required");
        }
        if (ownerIdentifier == null || ownerIdentifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        FileResource file = new FileResource();
        file.setId(UUID.randomUUID().toString());
        file.setFilename(filename);
        file.setOwnerEmail(ownerIdentifier);
        store.put(file.getId(), file);
        return file;
    }

    public FileResource upload(MultipartFile multipartFile, String ownerIdentifier) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        if (ownerIdentifier == null || ownerIdentifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

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
        store.put(file.getId(), file);
        return file;
    }

    public FileResource getForRead(String id, String userIdentifier) {
        FileResource file = getById(id);
        enforceDac(file, userIdentifier);
        return file;
    }

    public Path getPathForDownload(String id, String userIdentifier) {
        FileResource file = getById(id);
        enforceDac(file, userIdentifier);
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
        for (FileResource f : store.values()) {
            if (ownerIdentifier.equals(f.getOwnerEmail())) {
                out.add(f);
            }
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
        for (FileResource f : store.values()) {
            if (f.getAllowedUsers() != null && f.getAllowedUsers().contains(userIdentifier)) {
                out.add(f);
            }
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
        return file;
    }

    private FileResource getById(String id) {
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
        }
        FileResource file = store.get(id);
        if (file == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }
        return file;
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


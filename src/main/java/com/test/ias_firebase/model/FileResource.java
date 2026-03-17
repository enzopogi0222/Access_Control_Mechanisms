package com.test.ias_firebase.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Web DAC model: a resource owned by one user, with optional allowed users (ACL).
 * Used by FileResourceController to enforce discretionary access on /api/files.
 */
@Entity
@Table(name = "file_resources")
public class FileResource {
    @Id
    private String id;
    private String filename;
    private String storedFilename;
    private String contentType;
    private long sizeBytes;
    private Instant uploadedAt;
    private String ownerEmail;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "file_resource_acl", joinColumns = @JoinColumn(name = "file_id"))
    @Column(name = "allowed_uid")
    private List<String> allowedUsers = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private SecurityLevel classification = SecurityLevel.PUBLIC;

    public String getId() {
        return id;
    }

    public void setId(String id) { 
        
        this.id = id; 
    }

    public String getFilename() { 
       
        return filename; 
    }
    public void setFilename(String filename) {
        
        this.filename = filename; 
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public void setStoredFilename(String storedFilename) {
        this.storedFilename = storedFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getOwnerEmail() { 
        
        return ownerEmail; 
    }
    public void setOwnerEmail(String ownerEmail) { 
        
        this.ownerEmail = ownerEmail; 
    }

    public List<String> getAllowedUsers() { 
        
        return allowedUsers; 
    }
    public void setAllowedUsers(List<String> allowedUsers) {

        
        this.allowedUsers = allowedUsers != null ? allowedUsers : new ArrayList<>();
    }

    public SecurityLevel getClassification() {
        return classification;
    }

    public void setClassification(SecurityLevel classification) {
        this.classification = (classification != null) ? classification : SecurityLevel.PUBLIC;
    }
}

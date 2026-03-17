package com.test.ias_firebase.repo;

import com.test.ias_firebase.model.FileResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FileResourceRepository extends JpaRepository<FileResource, String> {
    List<FileResource> findByOwnerEmail(String ownerEmail);

    @Query("""
            select fr from FileResource fr
            join fr.allowedUsers au
            where au = :uid
            """)
    List<FileResource> findSharedWith(@Param("uid") String uid);
}

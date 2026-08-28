package com.project.repository;

import com.project.model.File;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<File, String> {

    List<File> findByRoomIdOrderByCreatedAtDesc(String roomId);

    @Query("SELECT f FROM File f WHERE f.roomId = :roomId ORDER BY f.createdAt DESC")
    List<File> findRecentFilesByRoomId(String roomId, int limit);

    Optional<File> findByStoredFilename(String storedFilename);
}
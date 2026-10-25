package com.project.repository;

import com.project.model.PinnedNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PinnedNoteRepository extends JpaRepository<PinnedNote, String> {
    List<PinnedNote> findByRoomIdOrderByPinnedAtDesc(String roomId);
    void deleteByRoomIdAndNoteId(String roomId, String noteId);
    void deleteByRoomId(String roomId);
}

package com.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "pinned_notes",
        indexes = @Index(name = "idx_pinned_notes_room_pinned", columnList = "room_id,pinned_at"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pinned_notes_room_note",
                columnNames = {"room_id", "note_id"}
        )
)
public class PinnedNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Column(name = "note_id", nullable = false)
    private String noteId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "pinned_at", nullable = false)
    private Instant pinnedAt;

    public PinnedNote() {
    }

    public PinnedNote(String roomId, String noteId, String payload, Instant pinnedAt) {
        this.roomId = roomId;
        this.noteId = noteId;
        this.payload = payload;
        this.pinnedAt = pinnedAt;
    }

    public String getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getNoteId() {
        return noteId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getPinnedAt() {
        return pinnedAt;
    }
}

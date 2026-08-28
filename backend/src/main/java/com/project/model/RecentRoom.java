package com.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;
import java.util.Objects;

@Embeddable
public class RecentRoom {

    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Column(name = "joined_at", columnDefinition = "TIMESTAMP")
    private Instant joinedAt;

    public RecentRoom() {
        this.joinedAt = Instant.now();
    }

    public RecentRoom(String roomId, Instant joinedAt) {
        this.roomId = roomId;
        this.joinedAt = joinedAt != null ? joinedAt : Instant.now();
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecentRoom that = (RecentRoom) o;
        return Objects.equals(roomId, that.roomId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomId);
    }
}

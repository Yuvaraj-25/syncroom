package com.project.dto;

import java.time.Instant;

public class RecentRoomDto {
    private String roomId;
    private Instant joinedAt;

    public RecentRoomDto() {
    }

    public RecentRoomDto(String roomId, Instant joinedAt) {
        this.roomId = roomId;
        this.joinedAt = joinedAt;
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
}

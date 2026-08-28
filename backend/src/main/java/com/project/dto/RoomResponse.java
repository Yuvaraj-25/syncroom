package com.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class RoomResponse {
    @JsonProperty("_id")
    private String id;

    private String roomId;
    private String ownerId;
    private boolean isPrivate;
    private Instant createdAt;
    private Instant updatedAt;

    public RoomResponse() {
    }

    public RoomResponse(String id, String roomId, String ownerId, boolean isPrivate, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.roomId = roomId;
        this.ownerId = ownerId;
        this.isPrivate = isPrivate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

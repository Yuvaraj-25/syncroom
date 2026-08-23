package com.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;

public class RoomResponse {
    @JsonProperty("_id")
    private String id;

    private String roomId;
    private String ownerId;
    private boolean isPrivate;
    private Date createdAt;
    private Date updatedAt;

    public RoomResponse() {
    }

    public RoomResponse(String id, String roomId, String ownerId, boolean isPrivate, Date createdAt, Date updatedAt) {
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

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}

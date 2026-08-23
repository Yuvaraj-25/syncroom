package com.project.dto;

import jakarta.validation.constraints.NotBlank;

public class RoomCreateRequest {
    @NotBlank(message = "RoomId is required")
    private String roomId;

    @NotBlank(message = "OwnerId is required")
    private String ownerId;

    private boolean isPrivate = false;
    private String password;

    public RoomCreateRequest() {
    }

    public RoomCreateRequest(String roomId, String ownerId, boolean isPrivate, String password) {
        this.roomId = roomId;
        this.ownerId = ownerId;
        this.isPrivate = isPrivate;
        this.password = password;
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

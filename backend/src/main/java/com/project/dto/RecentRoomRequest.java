package com.project.dto;

import jakarta.validation.constraints.NotBlank;

public class RecentRoomRequest {
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "RoomId is required")
    private String roomId;

    public RecentRoomRequest() {
    }

    public RecentRoomRequest(String email, String roomId) {
        this.email = email;
        this.roomId = roomId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
}

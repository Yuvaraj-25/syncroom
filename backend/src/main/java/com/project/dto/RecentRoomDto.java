package com.project.dto;

import java.util.Date;

public class RecentRoomDto {
    private String roomId;
    private Date joinedAt;

    public RecentRoomDto() {
    }

    public RecentRoomDto(String roomId, Date joinedAt) {
        this.roomId = roomId;
        this.joinedAt = joinedAt;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public Date getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Date joinedAt) {
        this.joinedAt = joinedAt;
    }
}

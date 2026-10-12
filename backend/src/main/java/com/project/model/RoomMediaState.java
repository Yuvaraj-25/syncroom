package com.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "room_media_state")
public class RoomMediaState {

    @Id
    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    public RoomMediaState() {
    }

    public RoomMediaState(String roomId, String payload) {
        this.roomId = roomId;
        this.payload = payload;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getPayload() {
        return payload;
    }
}

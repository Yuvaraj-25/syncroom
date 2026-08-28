package com.project.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JsonProperty("_id")
    private String id;

    @Column(name = "name")
    private String name;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "image", columnDefinition = "TEXT")
    private String image;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_recent_rooms", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "room_order")
    private List<RecentRoom> recentRooms = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP")
    private Instant updatedAt;

    public User() {
    }

    public User(String name, String email, String image) {
        this.name = name;
        this.email = email;
        this.image = image;
        this.recentRooms = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public List<RecentRoom> getRecentRooms() {
        if (recentRooms == null) {
            recentRooms = new ArrayList<>();
        }
        return recentRooms;
    }

    public void setRecentRooms(List<RecentRoom> recentRooms) {
        this.recentRooms = recentRooms;
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

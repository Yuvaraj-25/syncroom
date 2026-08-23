package com.project.dto;

import jakarta.validation.constraints.NotBlank;

public class UserSyncRequest {
    private String name;

    @NotBlank(message = "Email is required")
    private String email;

    private String image;

    public UserSyncRequest() {
    }

    public UserSyncRequest(String name, String email, String image) {
        this.name = name;
        this.email = email;
        this.image = image;
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
}

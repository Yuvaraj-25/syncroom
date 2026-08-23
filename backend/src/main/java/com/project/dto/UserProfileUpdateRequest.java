package com.project.dto;

import jakarta.validation.constraints.NotBlank;

public class UserProfileUpdateRequest {
    @NotBlank(message = "Email is required")
    private String email;

    private String name;
    private String image;

    public UserProfileUpdateRequest() {
    }

    public UserProfileUpdateRequest(String email, String name, String image) {
        this.email = email;
        this.name = name;
        this.image = image;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}

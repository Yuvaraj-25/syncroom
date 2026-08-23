package com.project.dto;

public class ProfileUpdateResponse {
    private String message;
    private UserProfileResponse user;

    public ProfileUpdateResponse() {
    }

    public ProfileUpdateResponse(String message, UserProfileResponse user) {
        this.message = message;
        this.user = user;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UserProfileResponse getUser() {
        return user;
    }

    public void setUser(UserProfileResponse user) {
        this.user = user;
    }
}

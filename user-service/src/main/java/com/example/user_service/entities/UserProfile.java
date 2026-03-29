package com.example.user_service.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column
    private String name;

    @Column
    private Long phoneNumber;

    @Column
    private String avatarUrl;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    public UserProfile() {}

    public UserProfile(String id, String name, Long phoneNumber, String avatarUrl, User user) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.avatarUrl = avatarUrl;
        this.user = user;
    }

    public static UserProfileBuilder builder() {
        return new UserProfileBuilder();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(Long phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    // Simple Builder class to support existing .builder() calls in AuthService
    public static class UserProfileBuilder {
        private User user;
        private String name;
        private Long phoneNumber;
        private String avatarUrl;

        public UserProfileBuilder user(User user) { this.user = user; return this; }
        public UserProfileBuilder name(String name) { this.name = name; return this; }
        public UserProfileBuilder phoneNumber(Long phoneNumber) { this.phoneNumber = phoneNumber; return this; }
        public UserProfileBuilder avatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; return this; }
        public UserProfile build() {
            UserProfile up = new UserProfile();
            up.setUser(user);
            up.setName(name);
            up.setPhoneNumber(phoneNumber);
            up.setAvatarUrl(avatarUrl);
            return up;
        }
    }
}
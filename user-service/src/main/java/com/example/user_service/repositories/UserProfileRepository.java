package com.example.user_service.repositories;

import com.example.user_service.entities.User;
import com.example.user_service.entities.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, String> {
	UserProfile findByUser(User user);
}

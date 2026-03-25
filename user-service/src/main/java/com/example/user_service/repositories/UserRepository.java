package com.example.user_service.repositories;

import com.example.user_service.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
	// Used for Login checks
	Optional<User> findByEmail(String email);

	// Used for Registration checks
	Boolean existsByEmail(String email);

	boolean existsByUsernameAndEmailNot(String username, String email);
}
package com.example.user_service.service;

import com.example.user_service.dto.UpdateProfileRequest;
import com.example.user_service.dto.UserProfileResponse;
import com.example.user_service.entities.User;
import com.example.user_service.entities.UserProfile;
import com.example.user_service.enums.Roles;
import com.example.user_service.repositories.UserProfileRepository;
import com.example.user_service.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;

	// ─────────────────────────────────────────
	// GET /users/me
	// ─────────────────────────────────────────
	public UserProfileResponse getMyProfile(String email) {
		log.info("Fetching profile for user: {}", email);

		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> {
					log.warn("User not found for email: {}", email);
					return new RuntimeException("User not found");
				});

		log.info("Profile fetched successfully for user: {}", email);
		return getUserProfileResponse(user, user.getProfile());
	}

	// ─────────────────────────────────────────
	// PUT /users/me
	// ─────────────────────────────────────────
	@Transactional
	public UserProfileResponse updateMyProfile(String email, UpdateProfileRequest request) {
		log.info("Updating profile for user: {}", email);

		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> {
					log.warn("User not found for email: {}", email);
					return new RuntimeException("User not found");
				});

		UserProfile profile = user.getProfile();

		if (request.username() != null) {
			if (userRepository.existsByUsernameAndEmailNot(request.username(), email)) {
				log.warn("Username already taken: {}", request.username());
				throw new RuntimeException("Username already taken");
			}
			log.debug("Updating username from: {} to: {}", user.getUsername(), request.username());
			user.setUsername(request.username());
		}
		if (request.name() != null) {
			log.debug("Updating name to: {}", request.name());
			profile.setName(request.name());
		}
		if (request.phoneNumber() != null) {
			log.debug("Updating phone number for user: {}", email);

			profile.setPhoneNumber(request.phoneNumber());
		}
		if (request.avatarUrl() != null) {
			log.debug("Updating avatar URL for user: {}", email);
			profile.setAvatarUrl(request.avatarUrl());
		}

		userRepository.save(user);
		log.info("Profile updated successfully for user: {}", email);

		return getUserProfileResponse(user, profile);
	}

	// ─────────────────────────────────────────
	// Shared mapper
	// ─────────────────────────────────────────
	private UserProfileResponse getUserProfileResponse(User user, UserProfile profile) {
		log.debug("Mapping user entity to profile response for: {}", user.getEmail());

		List<String> roleStrings = user.getRoles().stream()
				.map(Roles::name)
				.collect(Collectors.toList());

		return new UserProfileResponse(
				user.getId(),
				user.getEmail(),
				user.getUsername(),
				profile != null ? profile.getName()        : null,
				profile != null ? profile.getPhoneNumber() : null,
				profile != null ? profile.getAvatarUrl()   : null,
				roleStrings,
				user.isEmailVerified(),
				user.getCreatedAt().toInstant(ZoneOffset.UTC),
				user.getUpdatedAt().toInstant(ZoneOffset.UTC)
		);
	}
}

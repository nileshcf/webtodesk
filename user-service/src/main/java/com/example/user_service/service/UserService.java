package com.example.user_service.service;

import com.example.user_service.dto.UpdateProfileRequest;
import com.example.user_service.dto.UserProfileResponse;
import com.example.user_service.entities.User;
import com.example.user_service.entities.UserProfile;
import com.example.user_service.enums.Roles;
import com.example.user_service.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@Service
@RequiredArgsConstructor
public class UserService {

	private static final Logger log = LoggerFactory.getLogger(UserService.class);

	private final UserRepository userRepository;
	private final R2StorageService r2StorageService;

	// ─────────────────────────────────────────
	// GET /users/me
	// ─────────────────────────────────────────
	public UserProfileResponse getMyProfile(String email) {
		log.info("Fetching profile for user: {}", email);

		try {
			User user = userRepository.findByEmail(email)
					.orElseThrow(() -> {
						log.warn("User not found for email: {}", email);
						return new RuntimeException("User not found");
					});

			log.info("Profile fetched successfully for user: {}", email);
			return getUserProfileResponse(user, user.getProfile());
		} catch (RuntimeException e) {
			// Re-throw runtime exceptions as-is (they're already logged)
			throw e;
		} catch (Exception e) {
			log.error("Unexpected error fetching profile for user: {} - Error: {}", email, e.getMessage(), e);
			throw new RuntimeException("Failed to fetch user profile", e);
		}
	}

	// ─────────────────────────────────────────
	// PUT /users/me
	// ─────────────────────────────────────────
	@Transactional
	public UserProfileResponse updateMyProfile(String email, UpdateProfileRequest request) {
		log.info("Updating profile for user: {}", email);

		try {
			User user = userRepository.findByEmail(email)
					.orElseThrow(() -> {
						log.warn("User not found for email: {}", email);
						return new RuntimeException("User not found");
					});

			UserProfile profile = user.getProfile();
			boolean hasChanges = false;

			if (request.username() != null) {
				if (userRepository.existsByUsernameAndEmailNot(request.username(), email)) {
					log.warn("Username already taken: {} for user: {}", request.username(), email);
					throw new RuntimeException("Username already taken");
				}
				if (!request.username().equals(user.getUsername())) {
					log.debug("Updating username from: {} to: {} for user: {}", user.getUsername(), request.username(), email);
					user.setUsername(request.username());
					hasChanges = true;
				}
			}
			if (request.name() != null && !request.name().equals(profile.getName())) {
				log.debug("Updating name to: {} for user: {}", request.name(), email);
				profile.setName(request.name());
				hasChanges = true;
			}
			if (request.phoneNumber() != null && !request.phoneNumber().equals(profile.getPhoneNumber())) {
				log.debug("Updating phone number for user: {}", email);
				profile.setPhoneNumber(request.phoneNumber());
				hasChanges = true;
			}
			if (request.avatarUrl() != null && !request.avatarUrl().equals(profile.getAvatarUrl())) {
				log.debug("Updating avatar URL for user: {}", email);
				profile.setAvatarUrl(request.avatarUrl());
				hasChanges = true;
			}

			if (hasChanges) {
				userRepository.save(user);
				log.info("Profile updated successfully for user: {} with {} changes", email, 
					(request.username() != null ? 1 : 0) + 
					(request.name() != null ? 1 : 0) + 
					(request.phoneNumber() != null ? 1 : 0) + 
					(request.avatarUrl() != null ? 1 : 0));
			} else {
				log.info("No changes detected for user: {} profile update", email);
			}

			return getUserProfileResponse(user, profile);
		} catch (RuntimeException e) {
			// Re-throw runtime exceptions as-is (they're already logged)
			throw e;
		} catch (Exception e) {
			log.error("Unexpected error updating profile for user: {} - Error: {}", email, e.getMessage(), e);
			throw new RuntimeException("Failed to update user profile", e);
		}
	}

	// ─────────────────────────────────────────
	// POST /users/me/avatar
	// ─────────────────────────────────────────
	@Transactional
	public UserProfileResponse updateAvatar(String email, MultipartFile file) throws IOException {
		log.info("Updating avatar for user: {}", email);
		
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new RuntimeException("User not found"));
		
		String publicUrl = r2StorageService.uploadAvatar(file, user.getId());
		
		UserProfile profile = user.getProfile();
		profile.setAvatarUrl(publicUrl);
		userRepository.save(user);
		
		log.info("Avatar updated successfully for user: {}", email);
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

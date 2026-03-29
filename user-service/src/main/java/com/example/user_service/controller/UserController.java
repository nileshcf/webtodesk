package com.example.user_service.controller;

import com.example.user_service.dto.UpdateProfileRequest;
import com.example.user_service.dto.UserProfileResponse;
import com.example.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/me")
public class UserController {


	private final UserService userService;

	@GetMapping
	public ResponseEntity<UserProfileResponse> getMyProfile(@RequestHeader("X-User-Email") String email) {
		log.info("Received profile fetch request for user: {}", email);
		try {
			UserProfileResponse response = userService.getMyProfile(email);
			log.info("Profile fetch completed successfully for user: {}", email);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("Failed to fetch profile for user: {} - Error: {}", email, e.getMessage(), e);
			throw e;
		}
	}

	@PutMapping
	public ResponseEntity<UserProfileResponse> setMyProfile(
			@RequestHeader("X-User-Email") String email, 
			@RequestBody @Valid UpdateProfileRequest updateProfileRequest) {
		log.info("Received profile update request for user: {}", email);
		try {
			UserProfileResponse response = userService.updateMyProfile(email, updateProfileRequest);
			log.info("Profile update completed successfully for user: {}", email);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("Failed to update profile for user: {} - Error: {}", email, e.getMessage(), e);
			throw e;
		}
	}

}

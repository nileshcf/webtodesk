package com.example.user_service.controller;

import com.example.user_service.dto.UpdateProfileRequest;
import com.example.user_service.dto.UserProfileResponse;
import com.example.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/me")
public class UserController {


	private final UserService userService;

	@GetMapping
	public ResponseEntity<UserProfileResponse> getMyProfile(@RequestHeader("X-User-Email") String email) {
		return ResponseEntity.ok(userService.getMyProfile(email));
	}

	@PutMapping
	public ResponseEntity<UserProfileResponse> setMyProfile(@RequestHeader("X-User-Email") String email, @RequestBody @Valid UpdateProfileRequest updateProfileRequest) {
		return ResponseEntity.ok(userService.updateMyProfile(email, updateProfileRequest));
	}

}

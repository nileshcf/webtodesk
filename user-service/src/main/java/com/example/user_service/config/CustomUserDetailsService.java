package com.example.user_service.config;

import com.example.user_service.entities.User;
import com.example.user_service.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Slf4j
@Service
public class CustomUserDetailsService implements UserDetailsService {

	@Autowired
	private UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		log.debug("Attempting to load user by email: {}", email);
		
		try {
			User user = userRepository.findByEmail(email)
					.orElseThrow(() -> {
						log.warn("User not found with email: {}", email);
						return new UsernameNotFoundException("User not found with email: " + email);
					});

			log.info("Successfully loaded user details for email: {}", email);
			
			// In a real app, map your Role entities to Authorities here
			return new org.springframework.security.core.userdetails.User(
					user.getEmail(),
					user.getPassword(),
					new ArrayList<>()
			);
		} catch (DataAccessException e) {
			log.error("Database error while loading user by email: {} - Error: {}", email, e.getMessage(), e);
			throw new UsernameNotFoundException("Database error occurred while loading user", e);
		} catch (Exception e) {
			log.error("Unexpected error while loading user by email: {} - Error: {}", email, e.getMessage(), e);
			throw new UsernameNotFoundException("Unexpected error occurred while loading user", e);
		}
	}
}
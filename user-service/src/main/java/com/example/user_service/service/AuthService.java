package com.example.user_service.service;

import com.example.common.security.JwtConstants;
import com.example.common.security.JwtTokenProvider; // Import from Common
import com.example.common.security.JwtValidator;
import com.example.user_service.dto.*;
import com.example.user_service.entities.User;
import com.example.user_service.entities.UserProfile;
import com.example.user_service.enums.AuthProvider;
import com.example.user_service.enums.Roles;
import com.example.user_service.repositories.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import io.jsonwebtoken.Claims;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtValidator jwtValidator;
    private final RedisTemplate<String, String> redisTemplate;
    private final FirebaseAuth firebaseAuth;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            JwtValidator jwtValidator,
            RedisTemplate<String, String> redisTemplate,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            FirebaseAuth firebaseAuth) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtValidator = jwtValidator;
        this.redisTemplate = redisTemplate;
        this.firebaseAuth = firebaseAuth;
    }

    @Transactional
    public RegisterResponse register(SignupRequest request) {
        log.info("Starting user registration for email: {}", request.email());
        
        try {
            if (userRepository.existsByEmail(request.email())) {
                log.warn("Registration failed - email already exists: {}", request.email());
                throw new RuntimeException("Email already taken");
            }

            log.debug("Creating new user entity for email: {}", request.email());
            // 1. Create User
            User user = new User();
            user.setUsername(request.username());
            user.setEmail(request.email());
            user.setEmailVerified(false);
            user.setPassword(passwordEncoder.encode(request.password()));
            user.setRoles(List.of(Roles.ROLE_USER));
            user.setCreatedAt(LocalDateTime.now());

            log.debug("Creating user profile for email: {}", request.email());
            UserProfile profile = UserProfile.builder()
                    .user(user)
                    .phoneNumber(request.phoneNumber())
                    .build();

            user.setProfile(profile);
            userRepository.save(user);

            log.info("User {} registered successfully with ID: {}", user.getEmail(), user.getId());

            return new RegisterResponse("User registered successfully", user.getEmail(), user.getId(), user.getCreatedAt());
        } catch (RuntimeException e) {
            // Re-throw runtime exceptions as-is (they're already logged)
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during user registration for email: {} - Error: {}", request.email(), e.getMessage(), e);
            throw new RuntimeException("Failed to register user", e);
        }
    }

    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.email());

        try {
            // 1. Authenticate first — throws exception if credentials wrong
            try {
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(request.email(), request.password())
                );
                log.debug("Authentication successful for email: {}", request.email());
            } catch (Exception e) {
                log.error("Authentication failed for email: {} - Error: {}", request.email(), e.getMessage());
                throw e;
            }

            // 2. Fetch user — guaranteed to exist after authentication passes
            User user = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> {
                        log.error("User not found after successful authentication for email: {}", request.email());
                        return new RuntimeException("User not found");
                    });

            log.debug("Generating tokens for user: {} with roles: {}", user.getEmail(), user.getRoles());
            // 3. Convert Enum roles to Strings for JWT
            List<String> roleStrings = user.getRoles().stream()
                    .map(Roles::name)                         // ROLE_USER enum → "ROLE_USER" String
                    .collect(Collectors.toList());

            // 4. Prepare Claims
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", user.getId());
            claims.put("roles", roleStrings);                 // ✅ Strings in JWT, not Enum
            claims.put("email", user.getEmail());
            claims.put("username", user.getUsername());

            // 5. Generate tokens
            String accessToken  = jwtTokenProvider.generateAccessToken(
                    request.email(), claims, JwtConstants.ACCESS_TOKEN_EXPIRY);
            String refreshToken = jwtTokenProvider.generateRefreshToken(
                    request.email(), JwtConstants.REFRESH_TOKEN_EXPIRY);

            log.info("Login successful for email: {}", request.email());
            return new LoginResponse(
                    accessToken,
                    refreshToken,
                    "Bearer",
                    JwtConstants.ACCESS_TOKEN_EXPIRY / 1000,  // ✅ convert ms to seconds for frontend
                    user.getId(),
                    user.getEmail(),
                    roleStrings                               // ✅ return Strings not Enum to frontend
            );
        } catch (RuntimeException e) {
            // Re-throw runtime exceptions as-is (they're already logged)
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during login for email: {} - Error: {}", request.email(), e.getMessage(), e);
            throw new RuntimeException("Login failed due to unexpected error", e);
        }
    }

    public LoginResponse googleAuth(GoogleAuthRequest request) {
        if (firebaseAuth == null) {
            throw new IllegalStateException(
                    "Google sign-in is not configured. Set FIREBASE_CREDENTIALS_BASE64 to enable it.");
        }

        FirebaseToken decodedToken;
        try {
            decodedToken = firebaseAuth.verifyIdToken(request.getIdToken(), true);
        } catch (FirebaseAuthException e) {
            log.error("Invalid Firebase token", e);
            throw new RuntimeException("Invalid Firebase token", e);
        }

        String email = decodedToken.getEmail();
        String name = decodedToken.getName();
        String picture = decodedToken.getPicture();

        Optional<User> existingUser = userRepository.findByEmail(email);
        User user;

        if (existingUser.isEmpty()) {
            String dummyPassword = UUID.randomUUID().toString();
            String encodedPassword = passwordEncoder.encode(dummyPassword);

            user = new User();
            user.setUsername(email.split("@")[0] + "_" + UUID.randomUUID().toString().substring(0, 5));
            user.setEmail(email);
            user.setPassword(encodedPassword);
            user.setAuthProvider(AuthProvider.GOOGLE);
            user.setEmailVerified(true);
            user.setRoles(List.of(Roles.ROLE_USER));
            user.setCreatedAt(LocalDateTime.now());

            UserProfile profile = new UserProfile();
            profile.setName(name);
            profile.setAvatarUrl(picture);
            profile.setPhoneNumber(null);
            profile.setUser(user);

            user.setProfile(profile);
            userRepository.save(user);

            log.info("User {} registered successfully via Google", user.getEmail());
        } else {
            user = existingUser.get();
        }

        return generateLoginResponse(user);
    }

    private LoginResponse generateLoginResponse(User user) {
        List<String> roleStrings = user.getRoles().stream()
                .map(Roles::name)
                .collect(Collectors.toList());

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("roles", roleStrings);
        claims.put("email", user.getEmail());
        claims.put("username", user.getUsername());

        String accessToken  = jwtTokenProvider.generateAccessToken(
                user.getEmail(), claims, JwtConstants.ACCESS_TOKEN_EXPIRY);
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                user.getEmail(), JwtConstants.REFRESH_TOKEN_EXPIRY);

        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                JwtConstants.ACCESS_TOKEN_EXPIRY / 1000,
                user.getId(),
                user.getEmail(),
                roleStrings
        );
    }

    public RefreshResponse refreshToken(RefreshRequest request) {
        log.info("Refresh token request received");

        // 1. Validate refresh token
        Claims claims;
        try {
            claims = jwtValidator.validateRefreshToken(request.refreshToken());
        } catch (Exception e) {
            log.warn("Invalid refresh token: {}", e.getMessage());
            throw new RuntimeException("Refresh token is expired or invalid. Please login again");
        }

        // 2. Check Redis blacklist
        String blacklistKey = "blacklist:" + request.refreshToken();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
            log.warn("Blacklisted refresh token used");
            throw new RuntimeException("Refresh token has been invalidated. Please login again");
        }

        // 3. Extract email from claims
        String email = claims.getSubject();

        // 4. Fetch user from DB
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<String> roleStrings = user.getRoles().stream()
                .map(Roles::name)
                .collect(Collectors.toList());

        // 5. Build claims
        Map<String, Object> newClaims = new HashMap<>();
        newClaims.put("userId", user.getId());
        newClaims.put("roles", roleStrings);
        newClaims.put("email", user.getEmail());
        newClaims.put("username", user.getUsername());

        // 6. Generate new access token
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                email,
                newClaims,
                JwtConstants.ACCESS_TOKEN_EXPIRY
        );

        log.debug("New access token generated for: {}", email);

        return new RefreshResponse(
                newAccessToken,
                "Bearer",
                JwtConstants.ACCESS_TOKEN_EXPIRY / 1000
        );
    }

    public LogoutResponse logout(LogoutRequest request, String email) {
        log.info("Logout request received for: {}", email);

        // 1. Validate refresh token
        Claims claims;
        try {
            claims = jwtValidator.validateRefreshToken(request.refreshToken());
        } catch (Exception e) {
            log.warn("Invalid refresh token during logout: {}", e.getMessage());
            throw new RuntimeException("Refresh token is expired or invalid");
        }

        // 2. Calculate remaining TTL
        long remainingTTL = claims.getExpiration().getTime() - System.currentTimeMillis();

        if (remainingTTL <= 0) {
            // Token already expired — no need to blacklist
            log.debug("Refresh token already expired for: {}", email);
            return new LogoutResponse("Logged out successfully");
        }

        // 3. Blacklist refresh token in Redis
        String blacklistKey = "blacklist:" + request.refreshToken();
        redisTemplate.opsForValue().set(
                blacklistKey,
                email,              // store email as value — useful for debugging
                remainingTTL,
                TimeUnit.MILLISECONDS
        );

        log.info("Refresh token blacklisted for: {} TTL: {}ms", email, remainingTTL);

        return new LogoutResponse("Logged out successfully");
    }
}
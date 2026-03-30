package com.example.user_service.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.credentials.base64:}")
    private String firebaseCredentialsBase64;

    /**
     * Only creates the FirebaseAuth bean when credentials are provided.
     * Without this guard the app crashes on startup if FIREBASE_CREDENTIALS_BASE64
     * is empty — which blocks local dev for engineers who don't need Google sign-in.
     */
    @Bean
    @ConditionalOnProperty(name = "firebase.credentials.base64", matchIfMissing = false)
    public FirebaseAuth firebaseAuth() throws IOException {
        if (firebaseCredentialsBase64 == null || firebaseCredentialsBase64.isBlank()) {
            log.warn("FIREBASE_CREDENTIALS_BASE64 is empty — Google sign-in will be unavailable");
            return null;
        }

        byte[] decoded = Base64.getDecoder().decode(firebaseCredentialsBase64);
        InputStream credentialsStream = new ByteArrayInputStream(decoded);

        GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

        // Prevent duplicate initialization if another context refresh occurs
        FirebaseApp app;
        try {
            app = FirebaseApp.getInstance();
        } catch (IllegalStateException e) {
            app = FirebaseApp.initializeApp(options);
        }

        log.info("Firebase Admin SDK initialized successfully");
        return FirebaseAuth.getInstance(app);
    }
}

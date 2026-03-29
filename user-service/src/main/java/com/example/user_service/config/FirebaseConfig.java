package com.example.user_service.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials.base64}")
    private String firebaseCredentialsBase64;

    @Bean
    public FirebaseAuth firebaseAuth() throws IOException {
        byte[] decoded = Base64.getDecoder().decode(firebaseCredentialsBase64);
        InputStream credentialsStream = new ByteArrayInputStream(decoded);

        GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

        FirebaseApp app = FirebaseApp.initializeApp(options);
        return FirebaseAuth.getInstance(app);
    }
}

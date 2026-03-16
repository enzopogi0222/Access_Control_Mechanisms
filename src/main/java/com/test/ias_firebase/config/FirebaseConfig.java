package com.test.ias_firebase.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class FirebaseConfig {

    @Value("${app.firebase.service-account-path:}")
    private String serviceAccountPath;

    @PostConstruct
    public void init() throws IOException {
        InputStream serviceAccount = openServiceAccountStream();
        if (serviceAccount == null) {
            throw new RuntimeException(
                    "Firebase service account file not found. Set app.firebase.service-account-path in application.properties "
                    + "to the path of your serviceAccountKey.json (e.g. path/to/serviceAccountKey.json), or place "
                    + "firebase-service-account.json in src/main/resources/.");
        }

        try (InputStream stream = serviceAccount) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
        }
    }

    private InputStream openServiceAccountStream() throws IOException {
        if (StringUtils.hasText(serviceAccountPath)) {
            Path path = Path.of(serviceAccountPath);
            if (Files.isRegularFile(path)) {
                return new FileInputStream(serviceAccountPath);
            }
        }
        return getClass().getClassLoader().getResourceAsStream("firebase-service-account.json");
    }
}

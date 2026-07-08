package com.securechat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Secure Chat System.
 *
 * Bootstraps the Spring Boot application context with auto-configured
 * support for Web, WebSocket, Security, and JPA subsystems.
 */
@SpringBootApplication
@EnableScheduling
public class SecureChatApplication {

    public static void main(String[] args) {
        loadEnv();
        SpringApplication.run(SecureChatApplication.class, args);
    }

    private static void loadEnv() {
        try {
            java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
            if (java.nio.file.Files.exists(envPath)) {
                java.nio.file.Files.lines(envPath).forEach(line -> {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                        int index = line.indexOf("=");
                        String key = line.substring(0, index).trim();
                        String value = line.substring(index + 1).trim();
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        } else if (value.startsWith("'") && value.endsWith("'")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        System.setProperty(key, value);
                    }
                });
                System.out.println("Successfully loaded environment variables from .env");
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to load .env file: " + e.getMessage());
        }
    }
}

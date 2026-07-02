package com.example.addon.security;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Verifier {
    private static final String RESOURCE_PATH = "/assets/template/security/auth-hashes.json";
    private static final String WEBHOOK_ENV = "DUNE_WEBHOOK_URL";
    private static final Pattern ALGORITHM_PATTERN = Pattern.compile("\"algorithm\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern HASHES_ARRAY_PATTERN = Pattern.compile("\"allowedHashes\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern WEBHOOK_PATTERN = Pattern.compile("\"webhookUrl\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern HASH_PATTERN = Pattern.compile("\"([0-9a-fA-F]{64})\"");

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private Verifier() {
    }

    public static boolean verifyOrShutdown(Logger logger) {
        MinecraftClient client = MinecraftClient.getInstance();
        String username = client.getSession().getUsername();
        String webhookUrl = null;

        try {
            AuthConfig authConfig = loadAuthConfig();
            webhookUrl = authConfig.webhookUrl();
            String usernameHash = sha256Hex(username);

            if (authConfig.allowedHashes().contains(usernameHash)) {
                return true;
            }

            logger.error("DUNE : User not authorized, contact t3af");
            sendUnauthorizedWebhook(webhookUrl, username, logger);
            client.scheduleStop();
            return false;
        } catch (Exception e) {
            logger.error("DUNE : User not authorized, contact t3af", e);
            sendUnauthorizedWebhook(webhookUrl, username, logger);
            client.scheduleStop();
            return false;
        }
    }

    private static AuthConfig loadAuthConfig() throws IOException {
        try (InputStream stream = Verifier.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) throw new IOException("Missing auth hash resource: " + RESOURCE_PATH);

            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            String algorithm = extractAlgorithm(json);
            if (!"SHA-256".equalsIgnoreCase(algorithm)) {
                throw new IOException("Unsupported algorithm in auth hash file: " + algorithm);
            }

            Set<String> hashes = extractHashes(json);
            if (hashes.isEmpty()) throw new IOException("No allowed hashes found in auth hash file.");

            String webhookFromJson = extractWebhookUrl(json);
            String webhookUrl = webhookFromJson == null || webhookFromJson.isBlank()
                ? System.getenv(WEBHOOK_ENV)
                : webhookFromJson;

            return new AuthConfig(hashes, webhookUrl);
        }
    }

    private static String extractAlgorithm(String json) throws IOException {
        Matcher matcher = ALGORITHM_PATTERN.matcher(json);
        if (!matcher.find()) throw new IOException("Missing algorithm field in auth hash file.");
        return matcher.group(1);
    }

    private static Set<String> extractHashes(String json) throws IOException {
        Matcher arrayMatcher = HASHES_ARRAY_PATTERN.matcher(json);
        if (!arrayMatcher.find()) throw new IOException("Missing allowedHashes field in auth hash file.");

        Set<String> hashes = new HashSet<>();
        Matcher hashMatcher = HASH_PATTERN.matcher(arrayMatcher.group(1));
        while (hashMatcher.find()) {
            hashes.add(hashMatcher.group(1).toLowerCase());
        }
        return hashes;
    }

    private static String extractWebhookUrl(String json) {
        Matcher matcher = WEBHOOK_PATTERN.matcher(json);
        if (!matcher.find()) return null;
        return matcher.group(1);
    }

    private static String sha256Hex(String value) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));

        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static void sendUnauthorizedWebhook(String webhookUrl, String username, Logger logger) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            logger.warn("DUNE : unauthorized user detected but no webhook configured (set webhookUrl in auth-hashes.json or {} env var).", WEBHOOK_ENV);
            return;
        }

        String payload = "{\"content\":\"Unauthorized launch attempt by: " + escapeJson(username) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .thenAccept(response -> {
                if (response.statusCode() >= 400) {
                    logger.warn("DUNE : webhook returned HTTP {} for unauthorized user {}", response.statusCode(), username);
                }
            })
            .exceptionally(ex -> {
                logger.warn("DUNE : failed to send unauthorized webhook for user {}", username, ex);
                return null;
            });
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record AuthConfig(Set<String> allowedHashes, String webhookUrl) {
    }
}

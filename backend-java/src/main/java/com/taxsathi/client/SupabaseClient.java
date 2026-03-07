package com.taxsathi.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.taxsathi.exception.SupabaseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * SupabaseClient — low-level HTTP wrapper for Supabase REST, Storage, and Edge Function APIs.
 *
 * Design principles:
 * - All methods accept the user's JWT to honour Row Level Security (RLS) policies.
 * - Returns raw JSON strings to avoid coupling the client to any specific DTO shape.
 * - Throws SupabaseException (unchecked) on 4xx/5xx responses for clean error propagation.
 *
 * Equivalent to the Go services/supabase.go file.
 */
@Component
public class SupabaseClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String supabaseUrl;
    private final String anonKey;

    public SupabaseClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.anon-key}") String anonKey) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.supabaseUrl = supabaseUrl;
        this.anonKey = anonKey;
    }

    // -------------------------------------------------------------------------
    // Auth — validate Supabase JWT
    // -------------------------------------------------------------------------

    /**
     * Validates the user's JWT by calling Supabase Auth API.
     * Returns UserInfo (id, email) on success; throws SupabaseException on failure.
     */
    public UserInfo validateToken(String jwt) {
        HttpRequest request = baseRequest(supabaseUrl + "/auth/v1/user", jwt)
                .GET().build();

        String body = execute(request, "token validation");
        try {
            JsonNode node = objectMapper.readTree(body);
            return new UserInfo(
                    node.path("id").asText(),
                    node.path("email").asText()
            );
        } catch (JsonProcessingException e) {
            throw new SupabaseException("Failed to parse auth response: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // PostgREST — CRUD helpers
    // -------------------------------------------------------------------------

    /**
     * Queries a table and returns a JSON array string.
     */
    public String query(String table, String queryParams, String jwt) {
        String url = restUrl(table) + "?" + queryParams;
        HttpRequest request = baseRequest(url, jwt).GET().build();
        return execute(request, "query " + table);
    }

    /**
     * Queries a table expecting a single result. Returns null if not found (HTTP 406).
     */
    public String querySingle(String table, String queryParams, String jwt) {
        String url = restUrl(table) + "?" + queryParams;
        HttpRequest request = baseRequestBuilder(url, jwt)
                .header("Accept", "application/vnd.pgrst.object+json")
                .GET().build();

        HttpResponse<String> response = executeRaw(request, "querySingle " + table);
        if (response.statusCode() == 406) return null;
        if (response.statusCode() >= 400) {
            throw new SupabaseException("Supabase error " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    /**
     * Returns the count of rows matching the query (uses Content-Range header).
     */
    public int count(String table, String queryParams, String jwt) {
        String url = restUrl(table) + "?" + queryParams + "&select=id";
        HttpRequest request = baseRequestBuilder(url, jwt)
                .header("Prefer", "count=exact")
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = executeRaw(request, "count " + table);
        String contentRange = response.headers().firstValue("Content-Range").orElse("");
        if (contentRange.contains("/")) {
            String[] parts = contentRange.split("/");
            try {
                return Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    /**
     * Inserts a record and returns the resulting JSON.
     */
    public String insert(String table, Object data, String jwt) {
        String body = toJson(data);
        HttpRequest request = baseRequest(restUrl(table), jwt)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return execute(request, "insert " + table);
    }

    /**
     * Updates records matching the filter and returns the resulting JSON.
     */
    public String update(String table, String filter, Object data, String jwt) {
        String url = restUrl(table) + "?" + filter;
        String body = toJson(data);
        HttpRequest request = baseRequest(url, jwt)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return execute(request, "update " + table);
    }

    /**
     * Deletes records matching the filter.
     */
    public void delete(String table, String filter, String jwt) {
        String url = restUrl(table) + "?" + filter;
        HttpRequest request = baseRequest(url, jwt)
                .DELETE().build();
        execute(request, "delete " + table);
    }

    // -------------------------------------------------------------------------
    // Storage helpers
    // -------------------------------------------------------------------------

    /**
     * Uploads a file to Supabase Storage bucket.
     */
    public void storageUpload(String bucket, String path, byte[] data, String contentType, String jwt) {
        String url = storageUrl(bucket, path);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", anonKey)
                .header("Authorization", "Bearer " + jwt)
                .header("Content-Type", contentType)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofByteArray(data))
                .build();
        execute(request, "storage upload " + path);
    }

    /**
     * Downloads a file from Supabase Storage. Returns (bytes, contentType).
     */
    public StorageFile storageDownload(String bucket, String path, String jwt) {
        String url = storageUrl(bucket, path);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", anonKey)
                .header("Authorization", "Bearer " + jwt)
                .timeout(Duration.ofSeconds(60))
                .GET().build();

        try {
            HttpResponse<byte[]> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new SupabaseException("Storage download error " + response.statusCode());
            }
            String ct = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            return new StorageFile(response.body(), ct);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SupabaseException("Storage download failed: " + e.getMessage());
        }
    }

    /**
     * Deletes files from Supabase Storage.
     */
    public void storageDelete(String bucket, String[] paths, String jwt) {
        try {
            ObjectNode bodyNode = objectMapper.createObjectNode();
            bodyNode.set("prefixes", objectMapper.valueToTree(paths));
            String body = objectMapper.writeValueAsString(bodyNode);

            String url = supabaseUrl + "/storage/v1/object/" + bucket;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer " + jwt)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .DELETE().build();
            // Best-effort — ignore errors (Go backend also ignores errors here)
            executeRaw(request, "storage delete");
        } catch (JsonProcessingException ignored) {}
    }

    // -------------------------------------------------------------------------
    // Edge Functions
    // -------------------------------------------------------------------------

    /**
     * Invokes a Supabase Edge Function and returns the response body.
     */
    public String invokeEdgeFunction(String functionName, Object body, String jwt) {
        String url = supabaseUrl + "/functions/v1/" + functionName;
        String jsonBody = toJson(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", anonKey)
                .header("Authorization", "Bearer " + jwt)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        return execute(request, "edge function " + functionName);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String restUrl(String table) {
        return supabaseUrl + "/rest/v1/" + table;
    }

    private String storageUrl(String bucket, String path) {
        return supabaseUrl + "/storage/v1/object/" + bucket + "/" + path;
    }

    private HttpRequest.Builder baseRequestBuilder(String url, String jwt) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", anonKey)
                .header("Authorization", "Bearer " + jwt)
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .timeout(Duration.ofSeconds(30));
    }

    private HttpRequest.Builder baseRequest(String url, String jwt) {
        return baseRequestBuilder(url, jwt);
    }

    private String execute(HttpRequest request, String context) {
        HttpResponse<String> response = executeRaw(request, context);
        if (response.statusCode() >= 400) {
            throw new SupabaseException("Supabase " + context + " error " +
                    response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private HttpResponse<String> executeRaw(HttpRequest request, String context) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SupabaseException("HTTP " + context + " failed: " + e.getMessage());
        }
    }

    private String toJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new SupabaseException("JSON serialization failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Value objects
    // -------------------------------------------------------------------------

    public record UserInfo(String id, String email) {}

    public record StorageFile(byte[] data, String contentType) {}
}

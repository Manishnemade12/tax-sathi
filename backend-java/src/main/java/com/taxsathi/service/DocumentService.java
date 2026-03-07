package com.taxsathi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxsathi.client.SupabaseClient;
import com.taxsathi.exception.SupabaseException;
import com.taxsathi.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * DocumentService — manages tax document uploads, downloads, deletion, and AI analysis.
 * Mirrors Go's DocumentsHandler logic.
 */
@Service
public class DocumentService {

    private static final String BUCKET = "tax-documents";

    private final SupabaseClient supabase;
    private final ObjectMapper objectMapper;

    public DocumentService(SupabaseClient supabase, ObjectMapper objectMapper) {
        this.supabase = supabase;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists all documents for the authenticated user, ordered by creation date descending.
     */
    public String listDocuments() {
        String jwt = SecurityUtils.getUserJwt();
        return supabase.query("documents", "select=*&order=created_at.desc", jwt);
    }

    /**
     * Uploads a file to Supabase Storage (bucket: tax-documents) and creates a DB record.
     * Path format: {userId}/{timestamp}_{filename}
     */
    public String uploadDocument(MultipartFile file) throws IOException {
        String userId = SecurityUtils.getUserId();
        String jwt = SecurityUtils.getUserJwt();

        String filePath = userId + "/" + Instant.now().toEpochMilli() + "_" + file.getOriginalFilename();
        supabase.storageUpload(BUCKET, filePath, file.getBytes(),
                file.getContentType() != null ? file.getContentType() : "application/octet-stream", jwt);

        Map<String, Object> doc = new HashMap<>();
        doc.put("user_id", userId);
        doc.put("file_name", file.getOriginalFilename());
        doc.put("file_type", file.getContentType());
        doc.put("file_path", filePath);
        doc.put("file_size", file.getSize());
        doc.put("status", "uploaded");

        return supabase.insert("documents", doc, jwt);
    }

    /**
     * Deletes a document: removes storage object then DB record.
     * Verifies ownership via user_id filter (security check).
     */
    public void deleteDocument(String documentId) throws JsonProcessingException {
        String userId = SecurityUtils.getUserId();
        String jwt = SecurityUtils.getUserJwt();

        String docJson = supabase.querySingle("documents",
                "select=file_path&id=eq." + documentId + "&user_id=eq." + userId, jwt);
        if (docJson == null) {
            throw new IllegalArgumentException("document not found");
        }

        JsonNode node = objectMapper.readTree(docJson);
        String filePath = node.path("file_path").asText();

        supabase.storageDelete(BUCKET, new String[]{filePath}, jwt);
        supabase.delete("documents", "id=eq." + documentId + "&user_id=eq." + userId, jwt);
    }

    /**
     * Sends a document to the Supabase `analyze-document` Edge Function for AI extraction.
     * Downloads the file content and passes it so the edge function can process it.
     */
    public String analyzeDocument(String documentId) throws JsonProcessingException {
        String userId = SecurityUtils.getUserId();
        String jwt = SecurityUtils.getUserJwt();

        String docJson = supabase.querySingle("documents",
                "select=*&id=eq." + documentId + "&user_id=eq." + userId, jwt);
        if (docJson == null) {
            throw new IllegalArgumentException("document not found");
        }

        JsonNode docNode = objectMapper.readTree(docJson);
        String fileName = docNode.path("file_name").asText();
        String fileType = docNode.path("file_type").asText();
        long fileSize = docNode.path("file_size").asLong();
        String filePath = docNode.path("file_path").asText();
        String docId = docNode.path("id").asText();

        SupabaseClient.StorageFile storageFile = supabase.storageDownload(BUCKET, filePath, jwt);

        String content;
        if (fileType.contains("text") || fileType.contains("csv")) {
            content = new String(storageFile.data());
        } else {
            content = String.format("[Binary file: %s, type: %s, size: %d bytes]",
                    fileName, fileType, fileSize);
        }

        Map<String, Object> edgeFnBody = Map.of(
                "documentId", docId,
                "fileContent", content,
                "fileName", fileName
        );

        return supabase.invokeEdgeFunction("analyze-document", edgeFnBody, jwt);
    }
}

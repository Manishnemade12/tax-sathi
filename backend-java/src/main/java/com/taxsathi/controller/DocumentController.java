package com.taxsathi.controller;

import com.taxsathi.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * DocumentController — manages tax document lifecycle.
 *
 * Endpoints:
 *   GET    /api/documents              → list user's documents
 *   POST   /api/documents/upload       → upload a new document (multipart)
 *   DELETE /api/documents/{id}         → delete a document (storage + DB)
 *   POST   /api/documents/{id}/analyze → AI-analyze a document via Edge Function
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<String> listDocuments() {
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(documentService.listDocuments());
    }

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(documentService.uploadDocument(file));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) throws Exception {
        documentService.deleteDocument(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<String> analyze(@PathVariable String id) throws Exception {
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(documentService.analyzeDocument(id));
    }
}

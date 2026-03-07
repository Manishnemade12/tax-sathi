package com.taxsathi.controller;

import com.taxsathi.service.SchemesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SchemesController — manages tax scheme recommendations.
 *
 * Endpoints:
 *   GET  /api/schemes              → return cached scheme recommendations
 *   POST /api/schemes/personalized → generate fresh recommendations via Edge Function
 */
@RestController
@RequestMapping("/api/schemes")
public class SchemesController {

    private final SchemesService schemesService;

    public SchemesController(SchemesService schemesService) {
        this.schemesService = schemesService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSchemes() {
        return ResponseEntity.ok(schemesService.getSchemes());
    }

    @PostMapping("/personalized")
    public ResponseEntity<Map<String, Object>> getPersonalized() {
        return ResponseEntity.ok(schemesService.getPersonalized());
    }
}

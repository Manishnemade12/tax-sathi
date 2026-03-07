package com.taxsathi.controller;

import com.taxsathi.dto.TaxBuddyRequest;
import com.taxsathi.service.TaxBuddyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * TaxBuddyController — AI-driven ITR strategy generation endpoint.
 *
 * Endpoints:
 *   POST /api/taxbuddy/strategy → generate personalized tax strategy using Groq LLM
 */
@RestController
@RequestMapping("/api/taxbuddy")
public class TaxBuddyController {

    private final TaxBuddyService taxBuddyService;

    public TaxBuddyController(TaxBuddyService taxBuddyService) {
        this.taxBuddyService = taxBuddyService;
    }

    @PostMapping("/strategy")
    public ResponseEntity<Map<String, Object>> generateStrategy(@RequestBody TaxBuddyRequest request) {
        return ResponseEntity.ok(taxBuddyService.generateStrategy(request));
    }
}

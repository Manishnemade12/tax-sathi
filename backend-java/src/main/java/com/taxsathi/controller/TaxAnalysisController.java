package com.taxsathi.controller;

import com.taxsathi.dto.TaxAnalysisRequest;
import com.taxsathi.service.TaxAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * TaxAnalysisController — fetches and triggers AI tax analysis.
 *
 * Endpoints:
 *   GET  /api/tax-analysis       → get latest analysis result (null if no analysis yet)
 *   POST /api/tax-analysis/run   → save financial data + invoke Edge Function analysis
 */
@RestController
@RequestMapping("/api/tax-analysis")
public class TaxAnalysisController {

    private final TaxAnalysisService taxAnalysisService;

    public TaxAnalysisController(TaxAnalysisService taxAnalysisService) {
        this.taxAnalysisService = taxAnalysisService;
    }

    @GetMapping
    public ResponseEntity<String> getAnalysis() {
        String result = taxAnalysisService.getAnalysis();
        if (result == null) {
            // Return null as JSON literal (mirrors Go: w.Write([]byte("null")))
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body("null");
        }
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(result);
    }

    @PostMapping("/run")
    public ResponseEntity<String> runAnalysis(@RequestBody TaxAnalysisRequest request) {
        String result = taxAnalysisService.runAnalysis(request);
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body("{\"success\":true,\"data\":" + result + "}");
    }
}

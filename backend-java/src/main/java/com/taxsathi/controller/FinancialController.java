package com.taxsathi.controller;

import com.taxsathi.service.FinancialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * FinancialController — manages financial data CRUD.
 *
 * Endpoints:
 *   GET /api/financial-data   → get (or auto-create) financial data for current FY
 *   PUT /api/financial-data   → update allowed financial fields
 */
@RestController
@RequestMapping("/api/financial-data")
public class FinancialController {

    private final FinancialService financialService;

    public FinancialController(FinancialService financialService) {
        this.financialService = financialService;
    }

    @GetMapping
    public ResponseEntity<String> getFinancialData() {
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(financialService.getFinancialData());
    }

    @PutMapping
    public ResponseEntity<String> updateFinancialData(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(financialService.updateFinancialData(body));
    }
}

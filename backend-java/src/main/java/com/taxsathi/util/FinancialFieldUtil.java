package com.taxsathi.util;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * FinancialFieldUtil — centralises the allowed field whitelist and alias map.
 *
 * Equivalent to Go's financial_fields.go:
 * - allowedFinancialFields: set of valid fields accepted from the frontend
 * - financialFieldAliases: renames legacy/alternative keys to canonical names
 *
 * normalizeAndFilter() is called before every UPDATE to:
 *  1. Rename aliased keys
 *  2. Strip any unknown fields (safety whitelist)
 *  3. Strip immutable system fields (id, user_id, created_at, updated_at)
 */
@Component
public class FinancialFieldUtil {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "gross_salary",
            "hra_received",
            "lta_received",
            "other_income",
            "rental_income",
            "interest_income",
            "business_income",
            "deductions_80c",
            "deductions_80d",
            "deductions_80e",
            "deductions_80g",
            "deductions_nps",
            "deductions_hra",
            "deductions_lta",
            "standard_deduction",
            "other_deductions",
            "raw_data",
            "financial_year"
    );

    private static final Map<String, String> ALIASES = Map.of(
            "deduction_80c",    "deductions_80c",
            "deduction_80d",    "deductions_80d",
            "deduction_80e",    "deductions_80e",
            "deduction_80g",    "deductions_80g",
            "deduction_nps",    "deductions_nps",
            "hra_exemption",    "deductions_hra",
            "professional_tax", "other_deductions"
    );

    private static final Set<String> STRIP_KEYS = Set.of("id", "user_id", "created_at", "updated_at");

    /**
     * Takes a raw map from the frontend request, strips system keys,
     * applies aliases, and filters to allowed fields only.
     *
     * @param input raw map from request body
     * @return sanitised map safe to send to Supabase
     */
    public Map<String, Object> normalizeAndFilter(Map<String, Object> input) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            if (STRIP_KEYS.contains(key)) continue;
            // apply alias
            key = ALIASES.getOrDefault(key, key);
            if (ALLOWED_FIELDS.contains(key)) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }
}

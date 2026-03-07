package com.taxsathi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxsathi.client.SupabaseClient;
import com.taxsathi.util.FinancialFieldUtil;
import com.taxsathi.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * FinancialService — manages financial data for a user's financial year.
 * Mirrors Go's FinancialHandler.
 */
@Service
public class FinancialService {

    private final SupabaseClient supabase;
    private final FinancialFieldUtil fieldUtil;
    private final ObjectMapper objectMapper;

    @Value("${app.financial-year}")
    private String financialYear;

    public FinancialService(SupabaseClient supabase,
                            FinancialFieldUtil fieldUtil,
                            ObjectMapper objectMapper) {
        this.supabase = supabase;
        this.fieldUtil = fieldUtil;
        this.objectMapper = objectMapper;
    }

    /**
     * Gets financial data for the current financial year.
     * If no record exists, creates a blank record and returns it.
     */
    public String getFinancialData() {
        String userId = SecurityUtils.getUserId();
        String jwt = SecurityUtils.getUserJwt();

        String data = supabase.querySingle("financial_data",
                "select=*&user_id=eq." + userId + "&financial_year=eq." + financialYear, jwt);

        if (data == null) {
            Map<String, Object> newRecord = Map.of(
                    "user_id", userId,
                    "financial_year", financialYear
            );
            String inserted = supabase.insert("financial_data", newRecord, jwt);
            // PostgREST returns an array on insert — unwrap first element
            try {
                JsonNode arr = objectMapper.readTree(inserted);
                if (arr.isArray() && !arr.isEmpty()) {
                    return arr.get(0).toString();
                }
            } catch (Exception ignored) {}
            return inserted;
        }

        return data;
    }

    /**
     * Updates allowed financial fields for the current financial year.
     * Strips system/unknown fields via FinancialFieldUtil.
     */
    public String updateFinancialData(Map<String, Object> body) {
        String userId = SecurityUtils.getUserId();
        String jwt = SecurityUtils.getUserJwt();

        Map<String, Object> filtered = fieldUtil.normalizeAndFilter(body);
        if (filtered.isEmpty()) {
            throw new IllegalArgumentException("no valid financial fields provided");
        }

        return supabase.update("financial_data",
                "user_id=eq." + userId + "&financial_year=eq." + financialYear,
                filtered, jwt);
    }
}

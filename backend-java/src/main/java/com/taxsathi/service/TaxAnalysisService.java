package com.taxsathi.service;

import com.taxsathi.client.SupabaseClient;
import com.taxsathi.dto.TaxAnalysisRequest;
import com.taxsathi.util.FinancialFieldUtil;
import com.taxsathi.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * TaxAnalysisService — runs AI tax analysis via Supabase Edge Function.
 * Mirrors Go's TaxAnalysisHandler.
 */
@Service
public class TaxAnalysisService {

    private final SupabaseClient supabase;
    private final FinancialFieldUtil fieldUtil;

    @Value("${app.financial-year}")
    private String financialYear;

    public TaxAnalysisService(SupabaseClient supabase, FinancialFieldUtil fieldUtil) {
        this.supabase = supabase;
        this.fieldUtil = fieldUtil;
    }

    /**
     * Returns the latest tax analysis for the current financial year, or null if not found.
     */
    public String getAnalysis() {
        String userId = SecurityUtils.getUserId();
        String jwt = SecurityUtils.getUserJwt();

        String data = supabase.querySingle("tax_analyses",
                "select=*&user_id=eq." + userId + "&financial_year=eq." + financialYear, jwt);
        return data; // null is valid (no analysis yet)
    }

    /**
     * Saves updated financial data then invokes the `tax-analysis` Edge Function.
     * Returns {"success": true, "data": {...}} with the analysis result.
     */
    public String runAnalysis(TaxAnalysisRequest request) {
        String userId = SecurityUtils.getUserId();
        String jwt = SecurityUtils.getUserJwt();

        // Save financial data snapshot before running analysis
        if (request.getFinancialData() != null && !request.getFinancialData().isEmpty()) {
            Map<String, Object> filtered = fieldUtil.normalizeAndFilter(request.getFinancialData());
            if (!filtered.isEmpty()) {
                supabase.update("financial_data",
                        "user_id=eq." + userId + "&financial_year=eq." + financialYear,
                        filtered, jwt);
            }
        }

        Map<String, Object> edgeFnBody = new HashMap<>();
        edgeFnBody.put("financialData", request.getFinancialData());
        edgeFnBody.put("profile", request.getProfile());

        return supabase.invokeEdgeFunction("tax-analysis", edgeFnBody, jwt);
    }
}

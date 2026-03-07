package com.taxsathi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxsathi.client.SupabaseClient;
import com.taxsathi.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DashboardService — aggregates key stats for the dashboard view.
 * Mirrors Go's DashboardHandler.GetStats().
 *
 * Fetches:
 *  - document count
 *  - total income (sum of all income fields)
 *  - estimated tax (lower of old/new regime)
 *  - potential savings (difference between regimes)
 *  - income breakdown data for charts
 */
@Service
public class DashboardService {

    private final SupabaseClient supabase;
    private final ObjectMapper objectMapper;

    @Value("${app.financial-year}")
    private String financialYear;

    public DashboardService(SupabaseClient supabase, ObjectMapper objectMapper) {
        this.supabase = supabase;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getStats() {
        String userId = SecurityUtils.getUserId();
        String jwt = SecurityUtils.getUserJwt();

        int docCount = supabase.count("documents", "user_id=eq." + userId, jwt);

        String finDataJson = supabase.querySingle("financial_data",
                "select=*&user_id=eq." + userId + "&financial_year=eq." + financialYear, jwt);

        String analysisJson = supabase.querySingle("tax_analyses",
                "select=*&user_id=eq." + userId + "&financial_year=eq." + financialYear, jwt);

        double totalIncome = 0;
        double estimatedTax = 0;
        double savings = 0;
        List<Map<String, Object>> incomeData = buildIncomeData(finDataJson);

        if (finDataJson != null) {
            try {
                JsonNode fin = objectMapper.readTree(finDataJson);
                totalIncome = toDouble(fin, "gross_salary")
                        + toDouble(fin, "other_income")
                        + toDouble(fin, "rental_income")
                        + toDouble(fin, "interest_income")
                        + toDouble(fin, "business_income");
            } catch (JsonProcessingException ignored) {}
        }

        if (analysisJson != null) {
            try {
                JsonNode analysis = objectMapper.readTree(analysisJson);
                double oldTax = toDouble(analysis, "old_regime_tax");
                double newTax = toDouble(analysis, "new_regime_tax");
                estimatedTax = Math.min(oldTax, newTax);
                savings = Math.abs(oldTax - newTax);
            } catch (JsonProcessingException ignored) {}
        }

        Map<String, Object> result = new HashMap<>();
        result.put("documents", docCount);
        result.put("totalIncome", totalIncome);
        result.put("estimatedTax", estimatedTax);
        result.put("savings", savings);
        result.put("incomeData", incomeData);
        return result;
    }

    private List<Map<String, Object>> buildIncomeData(String finDataJson) {
        if (finDataJson == null) return null;

        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode fin = objectMapper.readTree(finDataJson);
            record IncomeItem(String name, String key) {}
            List<IncomeItem> items = List.of(
                    new IncomeItem("Salary", "gross_salary"),
                    new IncomeItem("Rental", "rental_income"),
                    new IncomeItem("Interest", "interest_income"),
                    new IncomeItem("Other", "other_income"),
                    new IncomeItem("Business", "business_income")
            );
            for (IncomeItem item : items) {
                double val = toDouble(fin, item.key());
                if (val > 0) {
                    result.add(Map.of("name", item.name(), "value", val));
                }
            }
        } catch (JsonProcessingException ignored) {}
        return result;
    }

    private double toDouble(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isNumber()) return n.asDouble();
        if (n.isTextual()) {
            try { return Double.parseDouble(n.asText()); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }
}

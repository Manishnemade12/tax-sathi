package com.taxsathi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxsathi.client.SupabaseClient;
import com.taxsathi.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * SchemesService — retrieves and generates tax scheme recommendations.
 * Mirrors Go's SchemesHandler.
 */
@Service
public class SchemesService {

    private final SupabaseClient supabase;
    private final ObjectMapper objectMapper;

    @Value("${app.financial-year}")
    private String financialYear;

    public SchemesService(SupabaseClient supabase, ObjectMapper objectMapper) {
        this.supabase = supabase;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns cached scheme recommendations from the latest tax analysis.
     * Returns {"schemes": null} if no analysis exists yet.
     */
    public Map<String, Object> getSchemes() {
        String userId = SecurityUtils.getUserId();
        String jwt = SecurityUtils.getUserJwt();

        String data = supabase.querySingle("tax_analyses",
                "select=scheme_recommendations&user_id=eq." + userId +
                        "&financial_year=eq." + financialYear, jwt);

        if (data == null) {
            return buildResult("schemes", null);
        }

        try {
            JsonNode node = objectMapper.readTree(data);
            JsonNode schemes = node.path("scheme_recommendations");
            return buildResult("schemes", schemes.isNull() ? null : schemes);
        } catch (JsonProcessingException e) {
            return buildResult("schemes", null);
        }
    }

    /**
     * Calls the `tax-analysis` Edge Function with current financial data and profile
     * to get fresh, personalized scheme recommendations.
     */
    public Map<String, Object> getPersonalized() {
        String userId = SecurityUtils.getUserId();
        String jwt = SecurityUtils.getUserJwt();

        // Fetch financial data and profile
        String finDataJson = supabase.querySingle("financial_data",
                "select=*&user_id=eq." + userId + "&financial_year=eq." + financialYear, jwt);
        String profileJson = supabase.querySingle("profiles",
                "select=*&user_id=eq." + userId, jwt);

        Map<String, Object> fin = parseOrEmpty(finDataJson);
        Map<String, Object> profile = parseOrEmpty(profileJson);

        Map<String, Object> edgeFnBody = Map.of("financialData", fin, "profile", profile);
        String resultJson = supabase.invokeEdgeFunction("tax-analysis", edgeFnBody, jwt);

        try {
            JsonNode resultData = objectMapper.readTree(resultJson);
            JsonNode schemesNode = resultData.path("scheme_recommendations");
            return Map.of(
                    "schemes", schemesNode.isNull() ? null : schemesNode,
                    "data", resultData
            );
        } catch (JsonProcessingException e) {
            return buildResult("schemes", null);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseOrEmpty(String json) {
        if (json == null) return new HashMap<>();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    private Map<String, Object> buildResult(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }
}

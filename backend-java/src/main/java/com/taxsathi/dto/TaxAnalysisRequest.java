package com.taxsathi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Request body for POST /api/tax-analysis/run
 */
public class TaxAnalysisRequest {

    @JsonProperty("financialData")
    private Map<String, Object> financialData;

    @JsonProperty("profile")
    private Map<String, Object> profile;

    public Map<String, Object> getFinancialData() { return financialData; }
    public void setFinancialData(Map<String, Object> financialData) { this.financialData = financialData; }

    public Map<String, Object> getProfile() { return profile; }
    public void setProfile(Map<String, Object> profile) { this.profile = profile; }
}

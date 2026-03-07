package com.taxsathi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Request body for POST /api/onboarding/complete
 */
public class OnboardingRequest {

    @JsonProperty("employment_type")
    private String employmentType;

    @JsonProperty("income_sources")
    private List<String> incomeSources;

    @JsonProperty("age_group")
    private String ageGroup;

    @JsonProperty("tax_regime")
    private String taxRegime;

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public List<String> getIncomeSources() { return incomeSources; }
    public void setIncomeSources(List<String> incomeSources) { this.incomeSources = incomeSources; }

    public String getAgeGroup() { return ageGroup; }
    public void setAgeGroup(String ageGroup) { this.ageGroup = ageGroup; }

    public String getTaxRegime() { return taxRegime; }
    public void setTaxRegime(String taxRegime) { this.taxRegime = taxRegime; }
}

package com.taxsathi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for POST /api/taxbuddy/strategy
 * Maps directly from the Go taxBuddyRequest struct.
 */
public class TaxBuddyRequest {

    @JsonProperty("age")
    private int age;

    @JsonProperty("res_status")
    private String resStatus;

    @JsonProperty("has_business")
    private boolean hasBusiness;

    @JsonProperty("has_cap_gains")
    private boolean hasCapGains;

    @JsonProperty("est_income")
    private double estIncome;

    @JsonProperty("has_agri")
    private boolean hasAgri;

    @JsonProperty("is_director")
    private boolean isDirector;

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getResStatus() { return resStatus; }
    public void setResStatus(String resStatus) { this.resStatus = resStatus; }

    public boolean isHasBusiness() { return hasBusiness; }
    public void setHasBusiness(boolean hasBusiness) { this.hasBusiness = hasBusiness; }

    public boolean isHasCapGains() { return hasCapGains; }
    public void setHasCapGains(boolean hasCapGains) { this.hasCapGains = hasCapGains; }

    public double getEstIncome() { return estIncome; }
    public void setEstIncome(double estIncome) { this.estIncome = estIncome; }

    public boolean isHasAgri() { return hasAgri; }
    public void setHasAgri(boolean hasAgri) { this.hasAgri = hasAgri; }

    public boolean isIsDirector() { return isDirector; }
    public void setIsDirector(boolean isDirector) { this.isDirector = isDirector; }
}

package com.taxsathi.service;

import com.taxsathi.client.SupabaseClient;
import com.taxsathi.dto.OnboardingRequest;
import com.taxsathi.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ProfileService — business logic for user profile operations.
 * Delegates all persistence to SupabaseClient (PostgREST).
 */
@Service
public class ProfileService {

    private static final Set<String> ALLOWED_PROFILE_FIELDS = Set.of(
            "full_name",
            "employment_type",
            "age_group",
            "tax_regime",
            "income_sources"
    );

    private final SupabaseClient supabase;

    @Value("${app.financial-year}")
    private String financialYear;

    public ProfileService(SupabaseClient supabase) {
        this.supabase = supabase;
    }

    /**
     * Retrieves the authenticated user's profile.
     * Returns the raw JSON string from Supabase.
     */
    public String getProfile() {
        String userId = SecurityUtils.getUserId();
        String jwt = SecurityUtils.getUserJwt();
        String data = supabase.querySingle("profiles", "select=*&user_id=eq." + userId, jwt);
        if (data == null) {
            throw new IllegalArgumentException("profile not found");
        }
        return data;
    }

    /**
     * Updates allowed profile fields. Strips any fields not in the whitelist.
     */
    public String updateProfile(Map<String, Object> body) {
        String userId = SecurityUtils.getUserId();
        String jwt = SecurityUtils.getUserJwt();

        Map<String, Object> update = new HashMap<>();
        body.forEach((k, v) -> {
            if (ALLOWED_PROFILE_FIELDS.contains(k)) update.put(k, v);
        });

        return supabase.update("profiles", "user_id=eq." + userId, update, jwt);
    }

    /**
     * Completes the onboarding flow:
     * 1. Updates profile with employment type, income sources, age group, tax regime
     * 2. Creates the default financial_data record for the current financial year
     */
    public void completeOnboarding(OnboardingRequest req) {
        String userId = SecurityUtils.getUserId();
        String jwt = SecurityUtils.getUserJwt();

        Map<String, Object> profileUpdate = new HashMap<>();
        profileUpdate.put("employment_type", req.getEmploymentType());
        profileUpdate.put("income_sources", req.getIncomeSources());
        profileUpdate.put("age_group", req.getAgeGroup());
        profileUpdate.put("tax_regime", req.getTaxRegime());
        profileUpdate.put("onboarding_completed", true);

        supabase.update("profiles", "user_id=eq." + userId, profileUpdate, jwt);

        // Seed financial data record (best-effort, ignore if already exists)
        Map<String, Object> finData = Map.of(
                "user_id", userId,
                "financial_year", financialYear
        );
        try {
            supabase.insert("financial_data", finData, jwt);
        } catch (Exception ignored) {
            // Record may already exist — not a failure
        }
    }
}

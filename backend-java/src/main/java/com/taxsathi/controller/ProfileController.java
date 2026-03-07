package com.taxsathi.controller;

import com.taxsathi.dto.OnboardingRequest;
import com.taxsathi.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ProfileController — manages user profile operations.
 *
 * Endpoints:
 *   GET  /api/profile                   → get authenticated user's profile
 *   PUT  /api/profile                   → update allowed profile fields
 *   POST /api/onboarding/complete       → complete onboarding flow
 */
@RestController
@RequestMapping("/api")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public ResponseEntity<String> getProfile() {
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(profileService.getProfile());
    }

    @PutMapping("/profile")
    public ResponseEntity<String> updateProfile(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(profileService.updateProfile(body));
    }

    @PostMapping("/onboarding/complete")
    public ResponseEntity<Map<String, Object>> completeOnboarding(@RequestBody OnboardingRequest req) {
        profileService.completeOnboarding(req);
        return ResponseEntity.ok(Map.of("success", true));
    }
}

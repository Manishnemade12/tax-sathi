package com.taxsathi.security;

/**
 * Immutable user principal stored in the Spring SecurityContext after JWT validation.
 * Carries the Supabase user ID, email, and raw JWT (needed for Supabase API calls).
 */
public record UserPrincipal(String userId, String email, String jwt) {
}

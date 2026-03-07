package com.taxsathi.util;

import com.taxsathi.security.UserPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility to extract the authenticated user's info from the Spring SecurityContext.
 * Equivalent to Go's middleware.GetUserID() and middleware.GetUserJWT() helpers.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static UserPrincipal getCurrentUser() {
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        if (principal instanceof UserPrincipal up) {
            return up;
        }
        throw new IllegalStateException("No authenticated user in security context");
    }

    public static String getUserId() {
        return getCurrentUser().userId();
    }

    public static String getUserJwt() {
        return getCurrentUser().jwt();
    }
}

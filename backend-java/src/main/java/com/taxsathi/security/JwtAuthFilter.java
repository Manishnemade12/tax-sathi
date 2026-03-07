package com.taxsathi.security;

import com.taxsathi.client.SupabaseClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT Authentication Filter — runs once per request before the controller.
 *
 * Flow:
 *  1. Check for "Authorization: Bearer <token>" header
 *  2. Call Supabase /auth/v1/user to validate the token and extract user ID + email
 *  3. Create a UserPrincipal and store it in SecurityContextHolder
 *  4. Downstream controllers retrieve userId via SecurityContextHolder.getContext()
 *
 * If no token or invalid token → Spring Security returns 401 for protected routes.
 * Public routes (/health) bypass this filter via SecurityConfig.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final SupabaseClient supabaseClient;

    public JwtAuthFilter(SupabaseClient supabaseClient) {
        this.supabaseClient = supabaseClient;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            SupabaseClient.UserInfo userInfo = supabaseClient.validateToken(token);

            UserPrincipal principal = new UserPrincipal(userInfo.id(), userInfo.email(), token);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            // Token invalid — security context remains empty; Spring Security will return 401
            logger.warn("JWT validation failed: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}

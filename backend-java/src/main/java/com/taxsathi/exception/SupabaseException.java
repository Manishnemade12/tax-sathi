package com.taxsathi.exception;

/**
 * Unchecked exception thrown when a Supabase API call fails.
 * Caught by GlobalExceptionHandler to return a structured JSON error response.
 */
public class SupabaseException extends RuntimeException {
    public SupabaseException(String message) {
        super(message);
    }
    public SupabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}

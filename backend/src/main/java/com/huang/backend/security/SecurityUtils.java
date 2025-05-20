package com.huang.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Utility class for Spring Security.
 */
@Component
@RequiredArgsConstructor
public class SecurityUtils {

    /**
     * Get the current user name from the security context.
     *
     * @return the current user name or empty optional if no authentication is available
     */
    public Optional<String> getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        
        return Optional.ofNullable(authentication.getName());
    }
} 
package com.aucontraire.gmailbuddy.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Test implementation of TokenAuthenticationFilter for unit testing.
 * This is a simple pass-through filter that doesn't perform any authentication logic.
 */
public class TestTokenAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Pass-through for testing
        filterChain.doFilter(request, response);
    }
}
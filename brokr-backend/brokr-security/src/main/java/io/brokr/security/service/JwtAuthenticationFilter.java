package io.brokr.security.service;

import io.brokr.security.model.ApiKeyAuthenticationToken;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String jwt = null;
        String userEmail = null;

        // First, try to get token from HttpOnly cookie (preferred, more secure)
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("brokr_token".equals(cookie.getName())) {
                    jwt = cookie.getValue();
                    break;
                }
            }
        }

        // Fallback to Authorization header for backward compatibility (e.g., API clients)
        if (jwt == null) {
            final String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwt = authHeader.substring(7);
            }
        }

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            userEmail = jwtService.extractUsername(jwt);

            if (userEmail != null) {
                // Check if API key authentication is already set - don't override it
                Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
                if (existingAuth instanceof ApiKeyAuthenticationToken) {
                    // API key authentication already set, skip JWT processing
                    filterChain.doFilter(request, response);
                    return;
                }
                
                // Process JWT token - always set authentication if valid (overrides anonymous/null)
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                if (jwtService.validateToken(jwt, userDetails)) {
                    // Reject challenge tokens - only accept fully verified tokens
                    if (jwtService.isChallengeToken(jwt)) {
                        // Don't set authentication - user needs to complete MFA
                        filterChain.doFilter(request, response);
                        return;
                    }

                    // Create authenticated token (3-parameter constructor with authorities automatically marks it as authenticated)
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (SignatureException e) {
            // Invalid token signature - treat as unauthenticated and continue filter chain
            // User will be redirected to login by Spring Security
        } catch (ExpiredJwtException e) {
            // Token expired - treat as unauthenticated and continue filter chain
        } catch (JwtException e) {
            // Any other JWT exception - treat as unauthenticated and continue filter chain
        } catch (Exception e) {
            // Unexpected error - treat as unauthenticated and continue filter chain
        }

        filterChain.doFilter(request, response);
    }
}
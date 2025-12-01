package io.brokr.security.config;

import io.brokr.security.service.ApiKeyAuthenticationFilter;
import io.brokr.security.service.JwtAuthenticationFilter;
import io.brokr.security.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Public auth endpoints (login, logout, register)
                        .requestMatchers("/auth/login", "/auth/logout", "/auth/register").permitAll()
                        // Protected auth endpoints (require authentication)
                        .requestMatchers("/auth/validate").authenticated()
                        // Other public endpoints
                        .requestMatchers("/actuator/health").permitAll()

                        // GraphQL endpoints - rely on method-level security (@PreAuthorize)
                        .requestMatchers("/graphql").permitAll()
                        .requestMatchers("/graphiql").hasAuthority("ROLE_SUPER_ADMIN")  // Only SUPER_ADMIN can access GraphiQL

                        // Admin only
                        .requestMatchers("/actuator/**").hasRole("SUPER_ADMIN")

                        // Static resources for frontend (must be public for login page to load)
                        // All JS/CSS files are in /assets/ directory, so no need for root-level /*.js or /*.css patterns
                        .requestMatchers("/", "/index.html", "/assets/**", "/*.ico", "/*.png", "/*.jpg", "/*.svg").permitAll()

                        // Allow all other GET requests for SPA routing (they will be handled by WebMvcConfig to serve index.html)
                        // This allows routes like /clusters/123, /login, etc. to work on page reload
                        .requestMatchers(this::isSpaRouteRequest).permitAll()

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                // API key filter runs FIRST (before JWT filter)
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // JWT filter runs second (after API key filter, before UsernamePasswordAuthenticationFilter)
                .addFilterAfter(jwtAuthFilter, ApiKeyAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Determines if a request is a SPA route that should be permitted.
     * This allows client-side routing to work on page reload.
     * 
     * @param request the HTTP request
     * @return true if the request is a GET request that should be handled by the SPA router
     */
    private boolean isSpaRouteRequest(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return false;
        }
        
        String requestURI = request.getRequestURI();
        
        // Exclude API and backend endpoints
        return !requestURI.startsWith("/api/") &&
               !requestURI.startsWith("/actuator/") &&
               !requestURI.startsWith("/auth/") &&
               !requestURI.equals("/graphql") &&
               !requestURI.equals("/graphiql");
    }
}
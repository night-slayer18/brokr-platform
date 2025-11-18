package io.brokr.security.service;

import io.brokr.core.model.User;
import io.brokr.security.model.BrokrUserDetails;
import io.brokr.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true) // Add this annotation
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Note: Spring Security's loadUserByUsername method is used with email for authentication
        User user = userRepository.findByEmail(email)
                .map(entity -> {
                    Hibernate.initialize(entity.getAccessibleEnvironmentIds());
                    return entity.toDomain();
                })
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return new BrokrUserDetails(user);
    }

    /**
     * Load user details by user ID (UUID).
     * Used for API key authentication where we have the user ID but not the email.
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
                .map(entity -> {
                    Hibernate.initialize(entity.getAccessibleEnvironmentIds());
                    return entity.toDomain();
                })
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));

        return new BrokrUserDetails(user);
    }
}
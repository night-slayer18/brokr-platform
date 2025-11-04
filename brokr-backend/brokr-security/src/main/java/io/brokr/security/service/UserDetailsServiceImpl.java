package io.brokr.security.service;

import io.brokr.core.model.User;
import io.brokr.security.model.BrokrUserDetails;
import io.brokr.storage.entity.UserEntity;
import io.brokr.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .map(UserEntity::toDomain)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return new BrokrUserDetails(user);
    }
}
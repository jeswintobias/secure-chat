package com.securechat.service;

import com.securechat.entity.User;
import com.securechat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Spring Security {@link UserDetailsService} implementation.
 *
 * Loads user details from the database for authentication and authorization.
 * This service bridges the gap between our JPA {@link User} entity and
 * Spring Security's {@link UserDetails} interface.
 */
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a user by username for Spring Security authentication.
     *
     * @param username the username to look up
     * @return a UserDetails object with credentials and authorities
     * @throws UsernameNotFoundException if no user with the given username exists
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + username)
                );

        if (user.isDeleted()) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        // Map our Role enum to Spring Security GrantedAuthority
        // Convention: ROLE_ prefix for role-based access control
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}

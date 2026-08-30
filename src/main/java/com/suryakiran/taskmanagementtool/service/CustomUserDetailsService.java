package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import com.suryakiran.taskmanagementtool.security.CustomUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

import static com.suryakiran.taskmanagementtool.util.LogSanitizer.maskEmail;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);
    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        logger.debug("Loading user by email: {}", maskEmail(email));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    logger.warn("User not found with email: {}", maskEmail(email));
                    return new UsernameNotFoundException("User not found");
                });

        logger.debug("User loaded with id: {}", user.getId());
        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                Collections.unmodifiableList(
                        user.getRoles().stream()
                                .map(role -> new SimpleGrantedAuthority(role.getName()))
                                .toList()
                )
        );
    }
}
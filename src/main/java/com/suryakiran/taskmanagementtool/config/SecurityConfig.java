package com.suryakiran.taskmanagementtool.config;

import com.suryakiran.taskmanagementtool.filter.JwtRequestFilter;
import com.suryakiran.taskmanagementtool.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final JwtRequestFilter jwtRequestFilter;
    private final Environment environment;

    public SecurityConfig(CustomUserDetailsService customUserDetailsService, JwtRequestFilter jwtRequestFilter, Environment environment) {
        this.customUserDetailsService = customUserDetailsService;
        this.jwtRequestFilter = jwtRequestFilter;
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (isDesktopProfileActive()) {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests
                            .anyRequest().permitAll()
                    );
        } else {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests
                            .requestMatchers("/api/authenticate", "/api/users/login", "/swagger-ui.html", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                            .anyRequest().authenticated()
                    )
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    );
            http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authBuilder.userDetailsService(customUserDetailsService).passwordEncoder(passwordEncoder());
        return authBuilder.build();
    }

    private boolean isDesktopProfileActive() {
        for (String profile : environment.getActiveProfiles()) {
            if (profile.equalsIgnoreCase("desktop")) {
                return true;
            }
        }
        return false;
    }
}
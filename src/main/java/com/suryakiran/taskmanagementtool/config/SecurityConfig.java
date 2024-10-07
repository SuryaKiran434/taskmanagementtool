package com.suryakiran.taskmanagementtool.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.profiles.active}")
    private String activeProfile;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if ("desktop".equals(activeProfile)) {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests
                            .anyRequest().permitAll()
                    );
        } else {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests
                            .requestMatchers("/", "/home","/api/tasks/home", "/static/**", "/css/**", "/js/**", "/images/**").permitAll()
                            .anyRequest().authenticated()
                    )
                    .formLogin(withDefaults())
                    .httpBasic(withDefaults());
        }

        return http.build();
    }

}
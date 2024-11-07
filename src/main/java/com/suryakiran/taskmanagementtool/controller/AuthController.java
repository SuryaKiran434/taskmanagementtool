package com.suryakiran.taskmanagementtool.controller;

import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import com.suryakiran.taskmanagementtool.util.JwtUtil;
import com.suryakiran.taskmanagementtool.exception.AuthenticationFailedException;
import com.suryakiran.taskmanagementtool.service.TokenBlacklistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserRepository userRepository; // Injecting UserRepository

    @Autowired
    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil,
                          TokenBlacklistService tokenBlacklistService, UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
        this.userRepository = userRepository; // Injected UserRepository
    }

    @PostMapping("/authenticate")
    public ResponseEntity<Map<String, String>> createAuthenticationToken(@RequestBody AuthRequest authRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getEmail(), authRequest.getPassword())
            );
            final UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            // Retrieve the User object from the database to get the userId
            User user = userRepository.findByEmail(authRequest.getEmail())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // Pass both userDetails and userId to generateToken
            String token = jwtUtil.generateToken(userDetails, user.getId());  // Pass userId here
            String refreshToken = jwtUtil.generateRefreshToken(userDetails); // Generate refresh token

            Map<String, String> tokens = new HashMap<>();
            tokens.put("token", token);
            tokens.put("refreshToken", refreshToken);

            return ResponseEntity.ok(tokens); // Return both tokens in response
        } catch (AuthenticationException e) {
            throw new AuthenticationFailedException("Incorrect email or password", e);
        }
    }


    @PostMapping("/refresh-token")
    public ResponseEntity<String> refreshToken(@RequestBody String refreshToken) {
        if (tokenBlacklistService.isBlacklisted(refreshToken)) {
            throw new AuthenticationFailedException("Invalid refresh token", new Throwable());
        }
        try {
            String newToken = jwtUtil.refreshToken(refreshToken);
            return ResponseEntity.ok(newToken);
        } catch (Exception e) {
            throw new AuthenticationFailedException("Invalid refresh token", e);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody String token) {
        tokenBlacklistService.blacklistToken(token);
        return ResponseEntity.ok().build();
    }
}

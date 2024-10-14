package com.suryakiran.taskmanagementtool.controller;

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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @Autowired
    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil, TokenBlacklistService tokenBlacklistService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @PostMapping("/authenticate")
    public String createAuthenticationToken(@RequestBody AuthRequest authRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getEmail(), authRequest.getPassword())
            );
            final UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return jwtUtil.generateToken(userDetails);
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
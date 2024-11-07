package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.LoginRequest;
import com.suryakiran.taskmanagementtool.dto.UserDTO;
import com.suryakiran.taskmanagementtool.exception.UserNotFoundException;
import com.suryakiran.taskmanagementtool.model.Role;
import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.repository.RoleRepository;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import com.suryakiran.taskmanagementtool.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        logger.info("Fetching all users");
        List<User> users = userRepository.findAll();
        return Collections.unmodifiableList(users.stream().map(this::convertToDTO).toList());
    }

    private UserDTO convertToDTO(User user) {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setFirstName(user.getFirstName());
        userDTO.setLastName(user.getLastName());
        userDTO.setEmail(user.getEmail());
        userDTO.setRoles(user.getRoles().stream().map(Role::getName).toList());
        userDTO.setCreatedAt(user.getCreatedAt());
        return userDTO;
    }

    @Override
    public User getUserById(int id) {
        logger.info("Fetching user with id: {}", id);
        return userRepository.findById((long) id).orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }

    @Override
    public User registerUser(User user) {
        logger.info("Creating user with email: {}", user.getEmail());
        validateRequiredFields(user);
        validatePassword(user.getPassword());

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("User Role not set."));
        user.setRoles(Set.of(userRole));

        return userRepository.save(user);
    }

    @Override
    public User updateUser(int id, User userDetails) {
        logger.info("Updating user with id: {}", id);

        User user = userRepository.findById((long) id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        // Only update fields if provided
        if (userDetails.getEmail() != null && !userDetails.getEmail().isEmpty()) {
            user.setEmail(userDetails.getEmail());
        }
        if (userDetails.getFirstName() != null) {
            user.setFirstName(userDetails.getFirstName());
        }
        if (userDetails.getLastName() != null) {
            user.setLastName(userDetails.getLastName());
        }

        // Set password only if provided
        if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDetails.getPassword()));
        }

        if (userDetails.getRoles() != null && !userDetails.getRoles().isEmpty()) {
            user.setRoles(userDetails.getRoles());
        }

        User updatedUser = userRepository.save(user);
        logger.info("User updated successfully with id: {}", updatedUser.getId());

        return updatedUser;
    }


    private void validateRequiredFields(User user) {
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            logger.error("Validation failed: Email is required");
            throw new IllegalArgumentException("Email is required");
        }
        if (user.getFirstName() == null || user.getFirstName().isEmpty()) {
            logger.error("Validation failed: First name is required");
            throw new IllegalArgumentException("First name is required");
        }
        if (user.getLastName() == null || user.getLastName().isEmpty()) {
            logger.error("Validation failed: Last name is required");
            throw new IllegalArgumentException("Last name is required");
        }
    }

    @Override
    public void deleteUser(int id) {
        logger.info("Deleting user with id: {}", id);
        userRepository.deleteById((long) id);
    }

    @Override
    public User assignAdminRoleToUser(int id) {
        logger.info("Assigning admin role to user with id: {}", id);
        User user = userRepository.findById((long) id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseThrow(() -> new RuntimeException("Admin Role not found"));
        user.getRoles().add(adminRole);
        return userRepository.save(user);
    }

    public User assignRoleToUser(Long userId, String roleName) {
        logger.info("Assigning role {} to user with id: {}", roleName, userId);
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Role role = roleRepository.findByName(roleName).orElseThrow(() -> new RuntimeException("Role not found"));
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    @Override
    public ResponseEntity<?> login(LoginRequest loginRequest) {
        Optional<User> optionalUser = userRepository.findByEmail(loginRequest.getEmail());
        if (optionalUser.isEmpty()) {
            logger.warn("Login failed: Invalid email {}", loginRequest.getEmail());
            return ResponseEntity.status(401).body("Invalid email or password");
        }
        User user = optionalUser.get();
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            logger.warn("Login failed: Invalid password for email {}", loginRequest.getEmail());
            return ResponseEntity.status(401).body("Invalid email or password");
        }
        Collection<? extends GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .toList();
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(), authorities);

        // Pass the userId to the generateToken method
        String token = jwtUtil.generateToken(userDetails, user.getId());  // Pass the userId here
        logger.info("User logged in successfully with email: {}", user.getEmail());
        return ResponseEntity.ok(token);
    }


    private void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (password.length() < 8) {
            logger.error("Password validation failed: Password must be at least 8 characters long");
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
        if (!Pattern.compile("[A-Z]").matcher(password).find()) {
            logger.error("Password validation failed: Password must contain at least one uppercase letter");
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }
        if (!Pattern.compile("[a-z]").matcher(password).find()) {
            logger.error("Password validation failed: Password must contain at least one lowercase letter");
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }
        if (!Pattern.compile("[!@#$%^&*()]").matcher(password).find()) {
            logger.error("Password validation failed: Password must contain at least one special character");
            throw new IllegalArgumentException("Password must contain at least one special character");
        }
    }
}

package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.LoginRequest;
import com.suryakiran.taskmanagementtool.dto.UserDTO;
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
        return userDTO;
    }

    @Override
    public User getUserById(int id) {
        logger.info("Fetching user with id: {}", id);
        return userRepository.findById((long) id).orElse(null);
    }

    @Override
    public User registerUser(User user) {
        logger.info("Creating user");
        validatePassword(user.getPassword());
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // Assign the USER role by default
        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("User Role not set."));
        user.setRoles(Set.of(userRole));

        return userRepository.save(user);
    }

    @Override
    public User updateUser(int id, User userDetails) {
        logger.info("Updating user with id: {}", id);
        validatePassword(userDetails.getPassword());
        User user = userRepository.findById((long) id).orElse(null);
        if (user != null) {
            user.setEmail(userDetails.getEmail());
            user.setPassword(passwordEncoder.encode(userDetails.getPassword()));
            user.setRoles(userDetails.getRoles());
            return userRepository.save(user);
        }
        logger.warn("User not found with id: {}", id);
        return null;
    }

    @Override
    public void deleteUser(int id) {
        logger.info("Deleting user with id: {}", id);
        userRepository.deleteById((long) id);
    }

    @Override
    public User assignAdminRoleToUser(int id) {
        logger.info("Assigning admin role to user with id: {}", id);
        Optional<User> optionalUser = userRepository.findById((long) id);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                    .orElseThrow(() -> new RuntimeException("Admin Role not found"));
            user.getRoles().add(adminRole);
            return userRepository.save(user);
        }
        logger.warn("User not found with id: {}", id);
        return null;
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
            return ResponseEntity.status(401).body("Invalid email or password");
        }
        User user = optionalUser.get();
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body("Invalid email or password");
        }
        Collection<? extends GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .toList();
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(), authorities);
        String token = jwtUtil.generateToken(userDetails);
        return ResponseEntity.ok(token);
    }

    private void validatePassword(String password) {
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
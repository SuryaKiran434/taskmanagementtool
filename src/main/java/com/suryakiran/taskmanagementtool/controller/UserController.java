
package com.suryakiran.taskmanagementtool.controller;

import com.suryakiran.taskmanagementtool.dto.LoginRequest;
import com.suryakiran.taskmanagementtool.dto.UserDTO;
import com.suryakiran.taskmanagementtool.dto.UserRegistrationDTO;
import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.service.UserService;
import com.suryakiran.taskmanagementtool.util.PasswordValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        logger.info("Fetching all users");
        List<UserDTO> users = userService.getAllUsers(); // Fetch users
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(users); // Return users in the response
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') or #id == authentication.principal.id")
    @GetMapping("/{id}")
    public User getUserById(@PathVariable int id, Authentication authentication) {
        logger.info("Fetching user with id: {}", id);
        return userService.getUserById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public User createUser(@RequestBody User user) {
        logger.info("Creating user with email: {}", user.getEmail());
        return userService.registerUser(user);
    }

    @PostMapping("/register")
    public User registerUser(@RequestBody UserRegistrationDTO userRegistrationDTO) {
        logger.info("Registering user with email: {}", userRegistrationDTO.getEmail());
        if (!PasswordValidator.validatePassword(userRegistrationDTO.getPassword())) {
            throw new IllegalArgumentException("Password does not meet complexity requirements");
        }
        User user = new User();
        user.setFirstName(userRegistrationDTO.getFirstName());
        user.setLastName(userRegistrationDTO.getLastName());
        user.setEmail(userRegistrationDTO.getEmail());
        user.setPassword(userRegistrationDTO.getPassword());
        return userService.registerUser(user);
    }
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        logger.info("Attempting to log in user with email: {}", loginRequest.getEmail());
        return userService.login(loginRequest);
    }

    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @PutMapping("/{id}")
    public User updateUser(@PathVariable int id, @RequestBody User userDetails, Authentication authentication) {
        logger.info("Updating user with id: {}", id);
        return userService.updateUser(id, userDetails);
    }

    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable int id, Authentication authentication) {
        logger.info("Deleting user with id: {}", id);
        userService.deleteUser(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/assign-admin")
    public User assignAdminRoleToUser(@PathVariable int id) {
        logger.info("Assigning admin role to user with id: {}", id);
        return userService.assignAdminRoleToUser(id);
    }
}
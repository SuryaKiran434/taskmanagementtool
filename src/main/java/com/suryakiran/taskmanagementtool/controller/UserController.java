package com.suryakiran.taskmanagementtool.controller;

import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<User> getAllUsers() {
        logger.info("Fetching all users");
        return userService.getAllUsers();
    }

    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @GetMapping("/{id}")
    public User getUserById(@PathVariable int id, Authentication authentication) {
        logger.info("Fetching user with id: {}", id);
        return userService.getUserById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public User createUser(@RequestBody User user) {
        logger.info("Creating user with email: {}", user.getEmail());
        return userService.createUser(user);
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
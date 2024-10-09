package com.suryakiran.taskmanagementtool.controller;

import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id, Authentication authentication) {
        return userService.getUserById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public User createUser(@RequestBody User user) {
        return userService.createUser(user);
    }

    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User userDetails, Authentication authentication) {
        return userService.updateUser(id, userDetails);
    }

    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id, Authentication authentication) {
        userService.deleteUser(id);
    }
}
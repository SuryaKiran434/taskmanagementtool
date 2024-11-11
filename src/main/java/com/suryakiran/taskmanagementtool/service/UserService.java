package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.LoginRequest;
import com.suryakiran.taskmanagementtool.dto.UserDTO;
import com.suryakiran.taskmanagementtool.model.User;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface UserService {
    List<UserDTO> getAllUsers();
    User getUserById(int id);
    User registerUser(User user);
    User updateUser(int id, User userDetails);
    void deleteUser(int id);
    User assignAdminRoleToUser(int id);
    ResponseEntity<?> login(LoginRequest loginRequest);
    void resetPassword(String email, String newPassword);
}
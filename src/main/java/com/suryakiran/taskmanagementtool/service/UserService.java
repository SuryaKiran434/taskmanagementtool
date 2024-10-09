package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.model.User;
import java.util.List;

public interface UserService {
    List<User> getAllUsers();
    User getUserById(int id);
    User createUser(User user);
    User updateUser(int id, User userDetails);
    void deleteUser(int id);
    User assignAdminRoleToUser(int id);
}
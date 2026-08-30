package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.UserDTO;
import com.suryakiran.taskmanagementtool.exception.UserNotFoundException;
import com.suryakiran.taskmanagementtool.model.Role;
import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.repository.RoleRepository;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.suryakiran.taskmanagementtool.util.LogSanitizer.maskEmail;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    /**
     * Deliberately carries no address. This message is written to the log by
     * {@code GlobalExceptionHandler} and returned in the response body, so interpolating the
     * address here would put it back in plaintext one frame after the statements above took
     * the trouble to mask it -- and hand it to the caller as well.
     */
    private static final String USER_NOT_FOUND = "User not found";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserConversionService userConversionService;
    private final UserValidationService userValidationService;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder,
                           UserConversionService userConversionService,
                           UserValidationService userValidationService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.userConversionService = userConversionService;
        this.userValidationService = userValidationService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        logger.info("Fetching all users");
        List<User> users = userRepository.findAll();
        return Collections.unmodifiableList(userConversionService.convertToDTOList(users));
    }

    @Override
    public User getUserById(int id) {
        logger.info("Fetching user with id: {}", id);
        return userRepository.findById((long) id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }

    @Override
    public User getUserByEmail(String email) {
        // Logged after the lookup rather than before it: once the row is in hand there is a
        // stable, non-personal identifier to name the user by, which correlates across log
        // lines better than the address ever did and keeps the address out of the log.
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        logger.info("Fetched user with id: {}", user.getId());
        return user;
    }

    @Override
    public UserDTO convertToDTO(User user) {
        return userConversionService.convertToDTO(user);
    }

    @Override
    public User registerUser(User user) {
        logger.info("Creating user with email: {}", maskEmail(user.getEmail()));
        userValidationService.validateRequiredFields(user);
        userValidationService.validatePassword(user.getPassword());

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("User Role not set."));
        user.setRoles(Set.of(userRole));

        return userRepository.save(user);
    }

    @Override
    public User updateUser(int id, User userDetails) {
        logger.info("Updating user with id: {}", id);

        User user = userRepository.findById((long) id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        if (userDetails.getEmail() != null && !userDetails.getEmail().isEmpty()) {
            user.setEmail(userDetails.getEmail());
        }
        if (userDetails.getFirstName() != null) {
            user.setFirstName(userDetails.getFirstName());
        }
        if (userDetails.getLastName() != null) {
            user.setLastName(userDetails.getLastName());
        }
        if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
            userValidationService.validatePassword(userDetails.getPassword());
            user.setPassword(passwordEncoder.encode(userDetails.getPassword()));
        }
        if (userDetails.getRoles() != null && !userDetails.getRoles().isEmpty()) {
            user.setRoles(userDetails.getRoles());
        }

        User updatedUser = userRepository.save(user);
        logger.info("User updated successfully with id: {}", updatedUser.getId());
        return updatedUser;
    }

    @Override
    public void deleteUser(int id) {
        logger.info("Deleting user with id: {}", id);
        userRepository.deleteById((long) id);
    }

    @Override
    @Transactional
    public User assignAdminRoleToUser(int id) {
        logger.info("Assigning admin role to user with id: {}", id);
        User user = userRepository.findById((long) id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseThrow(() -> new RuntimeException("Admin Role not found"));
        user.getRoles().add(adminRole);
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User removeAdminRoleFromUser(int id) {
        logger.info("Removing admin role from user with id: {}", id);
        User user = userRepository.findById((long) id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        user.getRoles().removeIf(role -> "ROLE_ADMIN".equals(role.getName()));
        return userRepository.save(user);
    }

    public User assignRoleToUser(Long userId, String roleName) {
        logger.info("Assigning role {} to user with id: {}", roleName, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found"));
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    @Override
    public void resetPassword(String email, String newPassword) {
        logger.info("Resetting password for user with email: {}", maskEmail(email));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        userValidationService.validatePassword(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        logger.info("Password reset successfully for user id: {}", user.getId());
    }
}

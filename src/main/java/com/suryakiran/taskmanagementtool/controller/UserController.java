package com.suryakiran.taskmanagementtool.controller;

import com.suryakiran.taskmanagementtool.dto.AdminCreateUserDTO;
import com.suryakiran.taskmanagementtool.dto.ResetTokenDTO;
import com.suryakiran.taskmanagementtool.dto.UserDTO;
import com.suryakiran.taskmanagementtool.dto.UserRegistrationDTO;
import com.suryakiran.taskmanagementtool.dto.UserUpdateDTO;
import com.suryakiran.taskmanagementtool.exception.UserNotFoundException;
import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.service.PasswordResetService;
import com.suryakiran.taskmanagementtool.service.UserService;
import com.suryakiran.taskmanagementtool.util.PasswordValidator;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

import java.util.List;

import static com.suryakiran.taskmanagementtool.util.LogSanitizer.maskEmail;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    /**
     * Extensions an avatar may be stored under. The value written into the filename is
     * always one of these constants, never a substring of the client-supplied name, so
     * nothing the client controls can reach the filesystem path.
     */
    private static final List<String> ALLOWED_AVATAR_EXTENSIONS =
            List.of(".jpg", ".jpeg", ".png", ".gif", ".webp");
    private static final String DEFAULT_AVATAR_EXTENSION = ".jpg";

    @Value("${app.upload.dir}")
    private String uploadDir;

    private final UserService userService;
    private final PasswordResetService passwordResetService;

    public UserController(UserService userService, PasswordResetService passwordResetService) {
        this.userService = userService;
        this.passwordResetService = passwordResetService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        logger.info("Fetching all users");
        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(users);
    }

    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @GetMapping("/{id}")
    public User getUserById(@PathVariable int id, Authentication authentication) {
        logger.info("Fetching user with id: {}", id);
        return userService.getUserById(id);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser(Authentication authentication) {
        logger.info("Fetching current user profile");
        User user = userService.getUserByEmail(authentication.getName());
        return ResponseEntity.ok(userService.convertToDTO(user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody AdminCreateUserDTO dto) {
        logger.info("Admin creating user with email: {}", maskEmail(dto.getEmail()));
        User user = new User();
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPassword(dto.getPassword());
        User created = userService.registerUser(user);
        if (dto.isAssignAdmin()) {
            created = userService.assignAdminRoleToUser(created.getId());
        }
        return ResponseEntity.status(201).body(userService.convertToDTO(created));
    }

    @PostMapping("/register")
    public User registerUser(@Valid @RequestBody UserRegistrationDTO userRegistrationDTO) {
        logger.info("Registering user with email: {}", maskEmail(userRegistrationDTO.getEmail()));
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

    /**
     * Step 1 of password reset: request an OTP.
     * Returns the OTP in the response body (until email delivery is implemented).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ResetTokenDTO> forgotPassword(@RequestParam String email) {
        // Emitted before the branch, with wording that does not depend on the outcome, so
        // the log cannot be read as an enumeration oracle: a registered and an unregistered
        // address produce the identical line, just as they produce the identical 200.
        logger.info("Forgot password request for email: {}", maskEmail(email));
        // Both branches answer 200. A 404 for an unknown address would turn this
        // endpoint into a membership oracle, so the status code is deliberately
        // the same either way -- and the single exit point below keeps that
        // property visible rather than buried in a catch block.
        //
        // NOTE: the status code is only half of it. The two bodies still differ
        // -- one carries an OTP and its own wording, the other a null and
        // different wording -- so the endpoint remains distinguishable to a
        // caller, and returning the OTP at all means an unauthenticated caller
        // can complete a reset. That is tracked separately; this change is not
        // a fix for it and must not be read as one.
        ResetTokenDTO body;
        try {
            // Verify user exists before generating OTP
            userService.getUserByEmail(email);
            String otp = passwordResetService.generateOtp(email);
            body = new ResetTokenDTO(email, otp,
                    "OTP generated. Use it within 15 minutes to reset your password.");
        } catch (UserNotFoundException e) {
            body = new ResetTokenDTO(email, null,
                    "If an account exists for this email, an OTP has been sent.");
        }
        return ResponseEntity.ok(body);
    }

    /**
     * Step 2 of password reset: verify OTP and set new password.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(
            @RequestParam String email,
            @RequestParam String token,
            @RequestParam String newPassword) {
        logger.info("Password reset attempt for email: {}", maskEmail(email));

        if (!passwordResetService.validateOtp(email, token)) {
            return ResponseEntity.status(400).body("Invalid or expired OTP. Please request a new one.");
        }

        try {
            userService.resetPassword(email, newPassword);
            return ResponseEntity.ok("Password reset successfully.");
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(404).body("User not found with the provided email.");
        } catch (IllegalArgumentException e) {
            // Log the specific reason server-side; the client gets a fixed message so that
            // internal detail carried on the exception never reaches the response body.
            logger.error("Password reset rejected for email {}: {}", maskEmail(email), e.getMessage(), e);
            return ResponseEntity.badRequest().body("Password does not meet complexity requirements.");
        }
    }

    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @PutMapping("/{id}")
    public User updateUser(@PathVariable int id, @Valid @RequestBody UserUpdateDTO update,
                           Authentication authentication) {
        logger.info("Updating user with id: {}", id);
        // Mapped across one field at a time on purpose. Binding the User entity directly let
        // a caller send "roles" in the body, which updateUser then copied onto the persisted
        // user -- self-service privilege escalation. The DTO has no setter for roles, id,
        // createdAt or tasks, so those cannot arrive at all. See UserUpdateDTO.
        User userDetails = new User();
        userDetails.setFirstName(update.getFirstName());
        userDetails.setLastName(update.getLastName());
        userDetails.setEmail(update.getEmail());
        userDetails.setPassword(update.getPassword());
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

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/remove-admin")
    public User removeAdminRoleFromUser(@PathVariable int id) {
        logger.info("Removing admin role from user with id: {}", id);
        return userService.removeAdminRoleFromUser(id);
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<UserDTO> uploadAvatar(@RequestParam("file") MultipartFile file,
                                                 Authentication authentication) throws IOException {
        logger.info("Uploading avatar for user: {}", maskEmail(authentication.getName()));
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().build();
        }
        String filename = UUID.randomUUID() + safeAvatarExtension(file.getOriginalFilename());

        Path avatarDir = Paths.get(uploadDir, "avatars").toAbsolutePath().normalize();
        Files.createDirectories(avatarDir);

        // Defence in depth: resolve against the canonical base directory and refuse anything
        // that escapes it. Checked after normalize(), so "../" segments are already collapsed
        // rather than merely stripped.
        Path target = avatarDir.resolve(filename).normalize();
        if (!target.startsWith(avatarDir)) {
            logger.warn("Rejected avatar upload resolving outside the avatar directory for user: {}",
                    maskEmail(authentication.getName()));
            return ResponseEntity.badRequest().build();
        }

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        String avatarUrl = "/uploads/avatars/" + filename;
        User user = userService.getUserByEmail(authentication.getName());
        user.setAvatarUrl(avatarUrl);
        userService.updateUser(user.getId(), user);

        return ResponseEntity.ok(userService.convertToDTO(user));
    }

    /**
     * Derives the extension an uploaded avatar is stored under.
     *
     * <p>The client-supplied filename is only ever <em>compared</em> against a fixed
     * allowlist; the returned string is one of the {@link #ALLOWED_AVATAR_EXTENSIONS}
     * constants or {@link #DEFAULT_AVATAR_EXTENSION}. Any directory component the client
     * smuggled in ("photo.png/../../../etc/cron.d/job") is dropped before the comparison,
     * and an unrecognised extension falls back to the default rather than being echoed.</p>
     *
     * @param originalFilename the name reported by the client, which is entirely untrusted
     * @return a constant extension, safe to concatenate into a filename
     */
    private static String safeAvatarExtension(String originalFilename) {
        if (originalFilename == null) {
            return DEFAULT_AVATAR_EXTENSION;
        }
        // Strip any path the client sent, using both separators: a Windows client may send
        // a backslash-delimited path, and the server may run on either platform.
        int lastSeparator = Math.max(originalFilename.lastIndexOf('/'), originalFilename.lastIndexOf('\\'));
        String bareName = originalFilename.substring(lastSeparator + 1);

        int dot = bareName.lastIndexOf('.');
        if (dot < 0) {
            return DEFAULT_AVATAR_EXTENSION;
        }
        String candidate = bareName.substring(dot).toLowerCase(Locale.ROOT);

        for (String allowed : ALLOWED_AVATAR_EXTENSIONS) {
            if (allowed.equals(candidate)) {
                return allowed;
            }
        }
        return DEFAULT_AVATAR_EXTENSION;
    }
}

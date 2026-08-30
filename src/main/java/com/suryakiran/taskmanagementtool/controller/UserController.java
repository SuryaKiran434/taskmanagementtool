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
     * The one answer POST /forgot-password gives, whether or not the address has an
     * account. Held as a constant so the two paths cannot drift into two wordings
     * again -- that difference was itself the enumeration oracle.
     */
    private static final String RESET_REQUESTED_MESSAGE =
            "If an account exists for this email, a reset code has been sent.";

    /**
     * Extensions an avatar may be stored under. The value written into the filename is
     * always one of these constants, never a substring of the client-supplied name, so
     * nothing the client controls can reach the filesystem path.
     */
    private static final List<String> ALLOWED_AVATAR_EXTENSIONS =
            List.of(".jpg", ".jpeg", ".png", ".gif", ".webp");
    private static final String DEFAULT_AVATAR_EXTENSION = ".jpg";

    /**
     * Whether POST /forgot-password may return the OTP in its response body.
     *
     * <p>Defaults to false and is switched on only by the dev profile. With no email
     * delivery implemented, returning the OTP was the only way to complete a reset --
     * but the endpoint is permitAll, so anyone who could name an address could read
     * its OTP and hand it straight back to /reset-password, which is also permitAll.
     * That is an unauthenticated takeover of any account whose address is known, not
     * a convenience. Until a mailer exists, the reset flow is deliberately
     * non-functional outside dev rather than open to everyone.</p>
     */
    @Value("${app.password-reset.expose-otp:false}")
    private boolean exposeOtp;

    @Value("${app.upload.dir}")
    private String uploadDir;

    private final UserService userService;
    private final PasswordResetService passwordResetService;

    /**
     * Says so, loudly, when the OTP echo is on. A setting that turns an endpoint into
     * an account-takeover path should not be something you discover by reading YAML.
     */
    @jakarta.annotation.PostConstruct
    void warnIfOtpExposed() {
        if (exposeOtp) {
            logger.warn("app.password-reset.expose-otp is ON: /forgot-password will return "
                    + "the OTP in its response body. This is for local development only -- "
                    + "with it on, anyone who can name an address can reset that account.");
        }
    }

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
     *
     * <p>Answers 200 with the same body for every address, registered or not. The OTP
     * is returned only when {@code app.password-reset.expose-otp} is on, which the dev
     * profile sets and nothing else does.</p>
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ResetTokenDTO> forgotPassword(@RequestParam String email) {
        // Emitted before the branch, with wording that does not depend on the outcome, so
        // the log cannot be read as an enumeration oracle: a registered and an unregistered
        // address produce the identical line, just as they produce the identical 200.
        logger.info("Forgot password request for email: {}", maskEmail(email));
        // Same status and same wording whichever branch runs, so the response says
        // nothing about whether the address is registered. Equal status codes alone
        // were not enough: the two bodies used to differ in both the otp field and
        // the message, which is all a caller needs to enumerate accounts.
        String otp = null;
        try {
            // Verify user exists before generating OTP
            userService.getUserByEmail(email);
            String generated = passwordResetService.generateOtp(email);
            // The OTP leaves the server only where a developer has explicitly asked
            // for it. Everywhere else it is generated, stored, and never returned.
            if (exposeOtp) {
                otp = generated;
            }
        } catch (UserNotFoundException e) {
            logger.info("Password reset requested for an address with no account: {}",
                    maskEmail(email));
        }
        return ResponseEntity.ok(new ResetTokenDTO(email, otp, RESET_REQUESTED_MESSAGE));
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

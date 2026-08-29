package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.controller.UserController;
import com.suryakiran.taskmanagementtool.dto.UserDTO;
import com.suryakiran.taskmanagementtool.exception.GlobalExceptionHandler;
import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.service.PasswordResetService;
import com.suryakiran.taskmanagementtool.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security regressions for {@link UserController}, covering two CodeQL findings.
 *
 * <p><strong>java/path-injection</strong> — {@code POST /api/users/me/avatar} built the
 * stored filename as {@code UUID + originalFilename.substring(lastIndexOf('.'))}, so the
 * whole extension was attacker-chosen and reached {@code Path.resolve} unsanitised. It
 * could carry separators and {@code ..} segments, and — with no allowlist — any extension
 * at all, which matters because the upload directory is served publicly at
 * {@code /uploads/**}: {@code "avatar.html"} was enough to host attacker HTML on the
 * application's own origin. The extension now comes from a fixed allowlist and the
 * resolved target is checked against the normalised base directory.</p>
 *
 * <p><strong>java/error-message-exposure</strong> — {@code POST /api/users/reset-password}
 * echoed {@code e.getMessage()} from a caught {@code IllegalArgumentException} straight
 * into the response body. That catch is broad enough to pick up failures from deeper
 * layers, whose messages carry internal detail. The detail is now logged and the client
 * gets a fixed string.</p>
 */
class UserControllerSecurityTest {

    private static final String UUID_NAME_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|jpeg|png|gif|webp)";

    @TempDir
    Path uploadRoot;

    private UserService userService;
    private PasswordResetService passwordResetService;
    private MockMvc mockMvc;
    private User user;

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("owner@example.com", null, List.of());

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        passwordResetService = mock(PasswordResetService.class);

        UserController controller = new UserController(userService, passwordResetService);
        ReflectionTestUtils.setField(controller, "uploadDir", uploadRoot.toString());

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        user = new User();
        user.setId(1);
        user.setEmail("owner@example.com");
        when(userService.getUserByEmail(anyString())).thenReturn(user);
        when(userService.updateUser(anyInt(), org.mockito.ArgumentMatchers.any(User.class))).thenReturn(user);
        when(userService.convertToDTO(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(new UserDTO());
    }

    // --- java/path-injection ---

    /**
     * The traversal attempt. {@code lastIndexOf('.')} on this name yields
     * {@code "./pwned"}, which the old code appended to the UUID and handed to
     * {@code Path.resolve} without normalising or bounding it. Nothing may be written
     * outside the avatar directory, and the stored name must be a UUID with an
     * allowlisted extension — no fragment of the client's name.
     */
    @Test
    void avatarUploadRejectsPathTraversalInTheOriginalFilename() throws Exception {
        MockMultipartFile malicious = new MockMultipartFile(
                "file",
                "photo.png/../../pwned",
                "image/png",
                "not really an image".getBytes());

        mockMvc.perform(multipart("/api/users/me/avatar").file(malicious).principal(authentication))
                .andExpect(status().isOk());

        assertThat(uploadRoot.resolve("pwned")).doesNotExist();
        assertOnlyASafelyNamedFileWasWritten();
    }

    /**
     * The same attack with the other separator. A Windows client may report a
     * backslash-delimited path, and stripping {@code '/'} alone would not see it — on
     * POSIX the backslashes are simply legal filename characters, so the client's text
     * ends up in the stored name; on Windows it escapes the directory outright.
     */
    @Test
    void avatarUploadRejectsBackslashTraversalInTheOriginalFilename() throws Exception {
        MockMultipartFile malicious = new MockMultipartFile(
                "file",
                "photo.png\\..\\..\\pwned",
                "image/png",
                "not really an image".getBytes());

        mockMvc.perform(multipart("/api/users/me/avatar").file(malicious).principal(authentication))
                .andExpect(status().isOk());

        assertThat(uploadRoot.resolve("pwned")).doesNotExist();
        assertOnlyASafelyNamedFileWasWritten();
    }

    /**
     * The stored name must be a UUID plus an allowlisted extension — no fragment of the
     * client-supplied name survives into the path or the URL handed back to the client.
     */
    @Test
    void avatarUploadStoresAUuidNameWithAnAllowlistedExtension() throws Exception {
        MockMultipartFile png = new MockMultipartFile("file", "holiday.PNG", "image/png", "bytes".getBytes());

        mockMvc.perform(multipart("/api/users/me/avatar").file(png).principal(authentication))
                .andExpect(status().isOk());

        List<Path> written = everyFileUnder(uploadRoot);
        assertThat(written).hasSize(1);
        assertThat(written.get(0).getFileName().toString())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png");
    }

    /**
     * An extension nobody allowlisted — {@code .html}, {@code .svg}, {@code .jsp} — must
     * not be honoured even when it contains no traversal. {@code /uploads/**} is permitAll
     * and served as static content, so an attacker-chosen extension there is a way to host
     * active content on the application's own origin.
     */
    @Test
    void avatarUploadFallsBackToTheDefaultExtensionForAnUnknownOne() throws Exception {
        assertStoredExtension("payload.html", ".jpg");
        assertStoredExtension("payload.svg", ".jpg");
        assertStoredExtension("shell.jsp", ".jpg");
        assertStoredExtension("script.sh", ".jpg");
        assertStoredExtension("no-extension-at-all", ".jpg");
        assertStoredExtension("photo.png/../../etc/passwd", ".jpg");

        // ...while the extensions that are allowlisted survive, case-insensitively.
        assertStoredExtension("avatar.JPEG", ".jpeg");
        assertStoredExtension("avatar.webp", ".webp");
    }

    private void assertStoredExtension(String originalFilename, String expectedExtension) throws Exception {
        MockMultipartFile upload =
                new MockMultipartFile("file", originalFilename, "image/png", "bytes".getBytes());

        mockMvc.perform(multipart("/api/users/me/avatar").file(upload).principal(authentication))
                .andExpect(status().isOk());

        assertThat(user.getAvatarUrl())
                .as("stored avatar URL for original filename %s", originalFilename)
                .endsWith(expectedExtension);
    }

    // --- java/error-message-exposure ---

    /**
     * The broad {@code catch (IllegalArgumentException)} on /reset-password used to return
     * the exception's own message. A message originating deeper in the stack can name
     * internal components; the response must carry a fixed string instead.
     */
    @Test
    void resetPasswordDoesNotEchoTheExceptionMessage() throws Exception {
        when(passwordResetService.validateOtp(anyString(), anyString())).thenReturn(true);
        doThrow(new IllegalArgumentException(
                "could not execute statement [Table \"USERS\" not found]; SQL [update users set password=?]"))
                .when(userService).resetPassword(anyString(), anyString());

        MvcResult result = mockMvc.perform(post("/api/users/reset-password")
                        .param("email", "owner@example.com")
                        .param("token", "123456")
                        .param("newPassword", "NewPassw0rd!"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Password does not meet complexity requirements."))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("USERS", "SQL", "statement");
    }

    /**
     * The generic body must not come at the cost of the endpoint's other answers: a valid
     * reset still succeeds, and the two pre-existing branches keep their own messages.
     */
    @Test
    void resetPasswordStillSucceedsAndKeepsItsOtherMessages() throws Exception {
        when(passwordResetService.validateOtp(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/users/reset-password")
                        .param("email", "owner@example.com")
                        .param("token", "123456")
                        .param("newPassword", "NewPassw0rd!"))
                .andExpect(status().isOk())
                .andExpect(content().string("Password reset successfully."));

        when(passwordResetService.validateOtp(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/users/reset-password")
                        .param("email", "owner@example.com")
                        .param("token", "000000")
                        .param("newPassword", "NewPassw0rd!"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid or expired OTP. Please request a new one."));
    }

    /**
     * The invariant both traversal tests share: exactly one file exists, it sits directly
     * in the avatar directory, and its name is a UUID plus an allowlisted extension.
     */
    private void assertOnlyASafelyNamedFileWasWritten() throws Exception {
        List<Path> written = everyFileUnder(uploadRoot);
        assertThat(written).hasSize(1);
        assertThat(written.get(0).getParent()).isEqualTo(uploadRoot.resolve("avatars"));
        assertThat(written.get(0).getFileName().toString()).matches(UUID_NAME_PATTERN);
    }

    private static List<Path> everyFileUnder(Path root) throws Exception {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).toList();
        }
    }
}

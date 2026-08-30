package com.suryakiran.taskmanagementtool;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.suryakiran.taskmanagementtool.controller.UserController;
import com.suryakiran.taskmanagementtool.exception.GlobalExceptionHandler;
import com.suryakiran.taskmanagementtool.dto.UserDTO;
import com.suryakiran.taskmanagementtool.exception.UserNotFoundException;
import com.suryakiran.taskmanagementtool.model.Role;
import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.repository.RoleRepository;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import com.suryakiran.taskmanagementtool.service.PasswordResetService;
import com.suryakiran.taskmanagementtool.service.UserConversionService;
import com.suryakiran.taskmanagementtool.service.UserService;
import com.suryakiran.taskmanagementtool.service.UserServiceImpl;
import com.suryakiran.taskmanagementtool.service.UserValidationService;
import com.suryakiran.taskmanagementtool.util.LogSanitizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Email addresses must not reach the logs in plaintext.
 *
 * <p>{@link LogSanitizer#sanitize} made these values safe to <em>write</em> — it closes the
 * CWE-117 log-forging hole — but it does not make them appropriate to write. An address is
 * personal data, and a log is copied to a shipper, indexed, and kept long after the record
 * it describes. The account and password-reset flows logged the address on every request.</p>
 *
 * <p>Two remedies, in order of preference. Where a persisted user is in hand the id is
 * logged instead: a stable, non-personal identifier that correlates across log lines better
 * than an address does. Where there is no id yet — a registration before the insert, a
 * forgotten-password request for an address that may not resolve at all — the address is
 * masked by {@link LogSanitizer#maskEmail}, which keeps the first character and the domain.
 * That is deliberately not a hash: a hash over a guessable input space is still a
 * pseudonymous identifier, and it throws away the domain, which is the part an operator
 * reads.</p>
 */
class EmailMaskingTest {

    // --- the mask itself ---

    @Test
    void keepsTheFirstCharacterOfTheLocalPartAndTheWholeDomain() {
        assertThat(LogSanitizer.maskEmail("sam.jones@gmail.com")).isEqualTo("s***@gmail.com");
        assertThat(LogSanitizer.maskEmail("owner@example.com")).isEqualTo("o***@example.com");
    }

    @Test
    void handlesASingleCharacterLocalPart() {
        // Nothing left to hide in a one-character local part, but it must still not throw
        // and must still produce the masked shape rather than the address itself.
        assertThat(LogSanitizer.maskEmail("a@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    void keepsAMultiLabelDomainInFull() {
        // The domain is the half an operator actually uses -- which tenant, which provider,
        // which corporate mail host -- so a subdomain must survive intact.
        assertThat(LogSanitizer.maskEmail("dana@mail.corp.example.co.uk"))
                .isEqualTo("d***@mail.corp.example.co.uk");
    }

    @Test
    void withholdsAValueThatIsNotAnAddress() {
        // With no '@' there is no local part and no domain to reason about, so nothing is
        // kept. Guessing which half was which would leak the very thing being masked.
        assertThat(LogSanitizer.maskEmail("not-an-address")).isEqualTo("***");
        assertThat(LogSanitizer.maskEmail("' OR 1=1 --")).isEqualTo("***");
    }

    @Test
    void rendersNullAndBlankAsPlaceholdersRatherThanThrowing() {
        // These reach the mask from request parameters and unpersisted DTOs, so both are
        // reachable in production and neither may take the log statement down with it.
        assertThat(LogSanitizer.maskEmail(null)).isEqualTo("<null>");
        assertThat(LogSanitizer.maskEmail("")).isEqualTo("<blank>");
        assertThat(LogSanitizer.maskEmail("   ")).isEqualTo("<blank>");
    }

    @Test
    void keepsOnlyTheDomainWhenTheLocalPartIsEmpty() {
        assertThat(LogSanitizer.maskEmail("@example.com")).isEqualTo("***@example.com");
    }

    @Test
    void masksOnTheFinalAtSign() {
        // A quoted local part may legally contain '@'; the domain is what follows the last.
        assertThat(LogSanitizer.maskEmail("\"odd@local\"@example.com"))
                .isEqualTo("\"***@example.com");
    }

    /**
     * Masking is applied <em>in addition</em> to control-character stripping, not instead of
     * it. An address is still attacker-controlled input, so the CWE-117 defence has to
     * survive: a newline smuggled into the domain must not open a second log line.
     */
    @Test
    void masksInAdditionToSanitising() {
        String forged = "sam@evil.test\n2026-08-30 12:00:00 INFO  Admin role granted";

        String masked = LogSanitizer.maskEmail(forged);

        assertThat(masked).doesNotContain("\n").doesNotContain("\r");
        assertThat(masked.lines()).hasSize(1);
        assertThat(masked).doesNotContain("sam@evil.test");
        assertThat(masked).startsWith("s***@");
    }

    @Test
    void truncatesAnOverlongValueJustAsSanitiseDoes() {
        String flood = "a".repeat(5_000) + "@example.com";

        String masked = LogSanitizer.maskEmail(flood);

        assertThat(masked).isEqualTo("***");
    }

    /**
     * The two things these log lines are read for have to survive the mask: the same address
     * retrying repeatedly must still collide, and two different people on one domain must
     * not be told apart.
     */
    @Test
    void staysUsefulForCorrelationWithoutIdentifyingAnyone() {
        assertThat(LogSanitizer.maskEmail("sam@example.com"))
                .isEqualTo(LogSanitizer.maskEmail("sam@example.com"));
        assertThat(LogSanitizer.maskEmail("sam@example.com"))
                .isEqualTo(LogSanitizer.maskEmail("sarah@example.com"));
        assertThat(LogSanitizer.maskEmail("sam@example.com"))
                .isNotEqualTo(LogSanitizer.maskEmail("sam@other.test"));
    }

    // --- end to end through a real log statement ---

    private static final String ADDRESS = "sam.jones@gmail.com";

    private ListAppender<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger controllerLogger;
    private UserService userService;
    private PasswordResetService passwordResetService;
    private MockMvc mockMvc;

    @BeforeEach
    void attachAppender() {
        userService = mock(UserService.class);
        passwordResetService = mock(PasswordResetService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService, passwordResetService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        appender = new ListAppender<>();
        appender.start();
        controllerLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(UserController.class);
        controllerLogger.setLevel(Level.INFO);
        controllerLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        controllerLogger.detachAppender(appender);
    }

    /**
     * The representative flow: {@code POST /api/users/forgot-password} takes an address
     * straight off the query string and logs it before doing anything else.
     */
    @Test
    void forgotPasswordDoesNotWriteTheAddressToTheLog() throws Exception {
        when(userService.getUserByEmail(anyString())).thenReturn(new User());
        when(passwordResetService.generateOtp(anyString())).thenReturn("123456");

        mockMvc.perform(post("/api/users/forgot-password").param("email", ADDRESS))
                .andExpect(status().isOk());

        assertThat(rendered()).containsExactly("Forgot password request for email: s***@gmail.com");
        assertThat(rendered().get(0)).doesNotContain(ADDRESS).doesNotContain("sam.jones");
    }

    /**
     * The endpoint answers 200 for an unknown address on purpose, so nobody can probe which
     * addresses are registered. The log must not undo that: a line whose <em>wording</em>
     * differs between the two outcomes hands the same oracle to anyone with log access. The
     * statement sits before the branch and reads identically either way.
     */
    @Test
    void theLogLineIsIdenticalWhetherOrNotTheAddressIsRegistered() throws Exception {
        when(userService.getUserByEmail(anyString())).thenReturn(new User());
        when(passwordResetService.generateOtp(anyString())).thenReturn("123456");
        mockMvc.perform(post("/api/users/forgot-password").param("email", ADDRESS))
                .andExpect(status().isOk());
        String registered = rendered().get(0);

        appender.list.clear();
        when(userService.getUserByEmail(anyString()))
                .thenThrow(new UserNotFoundException("User not found with email: " + ADDRESS));
        mockMvc.perform(post("/api/users/forgot-password").param("email", ADDRESS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist());
        String unregistered = rendered().get(0);

        assertThat(unregistered).isEqualTo(registered);
    }

    @Test
    void theOtherAccountEndpointsDoNotWriteTheAddressEither() throws Exception {
        when(passwordResetService.validateOtp(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/users/reset-password")
                        .param("email", ADDRESS)
                        .param("token", "123456")
                        .param("newPassword", "NewPassw0rd!"))
                .andExpect(status().isOk());

        assertThat(rendered()).containsExactly("Password reset attempt for email: s***@gmail.com");
    }

    /**
     * The two entry points that create an account. Neither has an id at the point it logs —
     * nothing is persisted yet — so both mask.
     */
    @Test
    void neitherAccountCreationEndpointWritesTheAddressToTheLog() throws Exception {
        when(userService.registerUser(any(User.class))).thenReturn(new User());
        when(userService.convertToDTO(any(User.class))).thenReturn(new UserDTO());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Sam","lastName":"Jones","email":"sam.jones@gmail.com",
                                 "password":"NewPassw0rd!","assignAdmin":false}"""))
                .andExpect(status().isCreated());
        assertThat(rendered()).containsExactly("Admin creating user with email: s***@gmail.com");

        appender.list.clear();
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Sam","lastName":"Jones","email":"sam.jones@gmail.com",
                                 "password":"NewPassw0rd!"}"""))
                .andExpect(status().isOk());
        assertThat(rendered()).containsExactly("Registering user with email: s***@gmail.com");
    }

    /**
     * The preferred remedy, at the layer where it applies: once the row has been loaded
     * there is an id, and the id is what gets logged. No form of the address survives that
     * line, masked or otherwise. The statement that runs before the lookup has no id to use
     * and masks instead.
     */
    @Test
    void theServiceLayerLogsTheUserIdOnceTheUserIsLoaded() {
        UserRepository userRepository = mock(UserRepository.class);
        User user = new User();
        user.setId(4242);
        user.setEmail(ADDRESS);
        when(userRepository.findByEmail(ADDRESS)).thenReturn(Optional.of(user));
        UserServiceImpl service = newUserService(userRepository, mock(RoleRepository.class));

        List<String> lines = capture(UserServiceImpl.class, () -> {
            service.getUserByEmail(ADDRESS);
            service.resetPassword(ADDRESS, "NewPassw0rd!");
        });

        assertThat(lines).contains("Fetched user with id: 4242")
                .contains("Password reset successfully for user id: 4242")
                .contains("Resetting password for user with email: s***@gmail.com");
        assertThat(lines).noneMatch(line -> line.contains(ADDRESS));
    }

    /**
     * Registration is the one service-layer statement with no id available: the row has not
     * been inserted, so there is nothing to log but the address, masked.
     */
    @Test
    void theServiceLayerMasksTheAddressBeforeTheUserIsPersisted() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(new Role()));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        UserServiceImpl service = newUserService(userRepository, roleRepository);
        User user = new User();
        user.setEmail(ADDRESS);
        user.setPassword("NewPassw0rd!");

        List<String> lines = capture(UserServiceImpl.class, () -> service.registerUser(user));

        assertThat(lines).containsExactly("Creating user with email: s***@gmail.com");
    }

    /**
     * {@link PasswordResetService} is the one place with no way to do better than mask: it
     * holds the address and nothing else — no repository, so no id is reachable from it.
     * Every one of its statements, on every branch, has to mask.
     */
    @Test
    void everyOtpStatementMasksTheAddress() {
        PasswordResetService service = new PasswordResetService();

        List<String> lines = capture(PasswordResetService.class, () -> {
            String otp = service.generateOtp(ADDRESS);
            service.validateOtp("nobody@example.test", "000000");
            service.validateOtp(ADDRESS, "000000");
            service.validateOtp(ADDRESS, otp);
        });

        assertThat(lines).containsExactly(
                "OTP generated for email: s***@gmail.com",
                "OTP validation failed: no OTP found for email: n***@example.test",
                "OTP validation failed: incorrect OTP for email: s***@gmail.com",
                "OTP validated successfully for email: s***@gmail.com");
        assertThat(lines).noneMatch(line -> line.contains(ADDRESS));
    }

    private static UserServiceImpl newUserService(UserRepository userRepository,
                                                  RoleRepository roleRepository) {
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        return new UserServiceImpl(userRepository, roleRepository, passwordEncoder,
                mock(UserConversionService.class), mock(UserValidationService.class));
    }

    /** Runs {@code body} with an appender attached to {@code type}'s logger. */
    private static List<String> capture(Class<?> type, Runnable body) {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(type);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(captured);
        try {
            body.run();
        } finally {
            logger.detachAppender(captured);
        }
        return captured.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private List<String> rendered() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}

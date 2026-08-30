package com.suryakiran.taskmanagementtool;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.suryakiran.taskmanagementtool.controller.CommentController;
import com.suryakiran.taskmanagementtool.controller.LabelController;
import com.suryakiran.taskmanagementtool.controller.NotificationController;
import com.suryakiran.taskmanagementtool.controller.SubtaskController;
import com.suryakiran.taskmanagementtool.controller.UserController;
import com.suryakiran.taskmanagementtool.dto.CommentDTO;
import com.suryakiran.taskmanagementtool.dto.LabelDTO;
import com.suryakiran.taskmanagementtool.dto.ProjectDTO;
import com.suryakiran.taskmanagementtool.dto.SubtaskDTO;
import com.suryakiran.taskmanagementtool.dto.UserDTO;
import com.suryakiran.taskmanagementtool.filter.JwtRequestFilter;
import com.suryakiran.taskmanagementtool.model.Comment;
import com.suryakiran.taskmanagementtool.model.Label;
import com.suryakiran.taskmanagementtool.model.Project;
import com.suryakiran.taskmanagementtool.model.Subtask;
import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.repository.CommentRepository;
import com.suryakiran.taskmanagementtool.repository.LabelRepository;
import com.suryakiran.taskmanagementtool.repository.ProjectMemberRepository;
import com.suryakiran.taskmanagementtool.repository.ProjectRepository;
import com.suryakiran.taskmanagementtool.repository.SubtaskRepository;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import com.suryakiran.taskmanagementtool.service.ActivityLogService;
import com.suryakiran.taskmanagementtool.service.CommentServiceImpl;
import com.suryakiran.taskmanagementtool.exception.GlobalExceptionHandler;
import com.suryakiran.taskmanagementtool.exception.UserNotFoundException;
import com.suryakiran.taskmanagementtool.repository.RoleRepository;
import com.suryakiran.taskmanagementtool.service.CustomUserDetailsService;
import com.suryakiran.taskmanagementtool.service.LabelServiceImpl;
import com.suryakiran.taskmanagementtool.service.NotificationServiceImpl;
import com.suryakiran.taskmanagementtool.service.ProjectServiceImpl;
import com.suryakiran.taskmanagementtool.service.SubtaskServiceImpl;
import com.suryakiran.taskmanagementtool.service.TaskConversionService;
import com.suryakiran.taskmanagementtool.service.TaskExportService;
import com.suryakiran.taskmanagementtool.service.PasswordResetService;
import com.suryakiran.taskmanagementtool.service.TaskServiceImpl;
import com.suryakiran.taskmanagementtool.service.UserConversionService;
import com.suryakiran.taskmanagementtool.service.UserService;
import com.suryakiran.taskmanagementtool.service.UserServiceImpl;
import com.suryakiran.taskmanagementtool.service.UserValidationService;
import com.suryakiran.taskmanagementtool.util.JwtUtil;
import com.suryakiran.taskmanagementtool.util.UniqueIdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The second pass of the sweep begun in #74 and #75.
 *
 * <p>#74 made user-controlled log arguments safe to <em>write</em>; #75 stopped the account
 * and password-reset flows from writing addresses in plaintext. Both worked from a reviewed
 * inventory, and the inventory was not the whole codebase: the request-scoped paths — the
 * JWT filter, the user-details lookup, and the label, comment, subtask, notification,
 * project, task and export endpoints — were still writing the address on every call, several
 * of them not even sanitised.</p>
 *
 * <p>The remedies are the ones #75 established, applied in the same order of preference.
 * Where a persisted user is already in hand the id replaces the address outright, which is
 * the better outcome: it is not personal data and it correlates across log lines. Where no
 * id is reachable at the point of the statement — a controller that has only the
 * {@code Authentication}, or a filter running before any lookup — the address is masked.</p>
 *
 * <p>Each test below drives the real log statement through a real call and asserts on what
 * the appender received, so it fails if the statement is reworded back to the address.</p>
 */
class EmailInLogsSweepTest {

    private static final String EMAIL = "sam.jones@gmail.com";
    private static final String MASKED = "s***@gmail.com";
    private static final int USER_ID = 4242;

    private final List<ListAppender<ILoggingEvent>> attached = new ArrayList<>();

    @AfterEach
    void detachAppenders() {
        attached.forEach(ListAppender::stop);
        attached.clear();
        SecurityContextHolder.clearContext();
    }

    /** Attaches a fresh appender to {@code type}'s logger and turns the level down to TRACE. */
    private ListAppender<ILoggingEvent> capture(Class<?> type) {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(type);
        logger.setLevel(Level.TRACE);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        attached.add(appender);
        return appender;
    }

    private static List<String> lines(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** Every assertion in this class reduces to this pair: no address, and something usable. */
    private static void assertNoAddress(List<String> lines) {
        assertThat(lines).isNotEmpty();
        assertThat(lines).noneMatch(line -> line.contains(EMAIL));
        assertThat(lines).noneMatch(line -> line.contains("sam.jones"));
    }

    private static Authentication authenticationFor(String name) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(name);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenReturn(List.of());
        return authentication;
    }

    private static User persistedUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(EMAIL);
        user.setPassword("{noop}irrelevant");
        user.setFirstName("Sam");
        user.setLastName("Jones");
        user.setRoles(new HashSet<>());
        return user;
    }

    private static Task task(User owner) {
        Task task = new Task();
        task.setId("TASK-1");
        task.setTitle("Ship the sweep");
        task.setUser(owner);
        task.setLabels(new HashSet<>());
        return task;
    }

    // ------------------------------------------------------------------
    // JwtRequestFilter — the highest-volume leak in the codebase
    // ------------------------------------------------------------------

    /**
     * The filter runs on every authenticated request and logged the JWT subject — which is
     * the user's address — raw, so this was both the largest volume of plaintext addresses
     * and, since nothing sanitised it, the last unguarded log-forging sink from #74.
     *
     * <p>It is masked rather than replaced by an id. {@code loadUserByUsername} is declared
     * to return {@link UserDetails}, which carries no id; reaching one would mean either
     * downcasting on the hot path of every request or narrowing the override's return type,
     * and both break the moment the principal is any other {@code UserDetails}
     * implementation — as it is in {@link BearerOnlyAuthenticationTest}. A log line is not
     * worth taking that risk on the authentication path.</p>
     */
    private JwtRequestFilter filter;
    private CustomUserDetailsService userDetailsService;
    private JwtUtil jwtUtil;
    private final UserDetails principal = org.springframework.security.core.userdetails.User
            .withUsername(EMAIL).password("irrelevant").authorities("ROLE_USER").build();

    @BeforeEach
    void setUpFilter() {
        userDetailsService = mock(CustomUserDetailsService.class);
        jwtUtil = mock(JwtUtil.class);
        filter = new JwtRequestFilter(userDetailsService, jwtUtil);
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tasks");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    void theFilterMasksTheSubjectOnTheHappyPathAndStillAuthenticates() throws Exception {
        ListAppender<ILoggingEvent> log = capture(JwtRequestFilter.class);
        when(jwtUtil.extractUsername(anyString())).thenReturn(EMAIL);
        when(jwtUtil.validateToken(anyString(), any())).thenReturn(true);
        when(jwtUtil.extractRoles(anyString())).thenReturn(List.of("ROLE_USER"));
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(principal);

        filter.doFilter(bearerRequest("a.b.c"), new MockHttpServletResponse(), new MockFilterChain());

        // Behaviour first: masking a log argument must not cost anyone their session.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(EMAIL);

        assertNoAddress(lines(log));
        assertThat(lines(log)).anyMatch(line -> line.contains("JWT token present for user: " + MASKED));
        assertThat(lines(log)).anyMatch(line -> line.contains("Authenticated user: " + MASKED));
    }

    @Test
    void theFilterMasksTheSubjectWhenATokenCarriesNoRoles() throws Exception {
        ListAppender<ILoggingEvent> log = capture(JwtRequestFilter.class);
        when(jwtUtil.extractUsername(anyString())).thenReturn(EMAIL);
        when(jwtUtil.validateToken(anyString(), any())).thenReturn(true);
        when(jwtUtil.extractRoles(anyString())).thenReturn(List.of());
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(principal);

        filter.doFilter(bearerRequest("a.b.c"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertNoAddress(lines(log));
        assertThat(lines(log)).anyMatch(line -> line.contains("no roles found for user: " + MASKED));
    }

    @Test
    void theFilterMasksTheSubjectWhenValidationFails() throws Exception {
        ListAppender<ILoggingEvent> log = capture(JwtRequestFilter.class);
        when(jwtUtil.extractUsername(anyString())).thenReturn(EMAIL);
        when(jwtUtil.validateToken(anyString(), any())).thenReturn(false);
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(principal);

        filter.doFilter(bearerRequest("a.b.c"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertNoAddress(lines(log));
        assertThat(lines(log)).anyMatch(line ->
                line.contains("Token validation failed for user: " + MASKED) && line.contains("/api/tasks"));
    }

    /**
     * The subject was previously written raw, so an address containing a line break — which
     * reaches the token from whatever the account was registered under — could open a second
     * log line. {@code maskEmail} routes through {@code sanitize}, so it cannot any more.
     */
    @Test
    void theFilterCannotBeMadeToForgeALogLineThroughTheSubject() throws Exception {
        ListAppender<ILoggingEvent> log = capture(JwtRequestFilter.class);
        when(jwtUtil.extractUsername(anyString()))
                .thenReturn("sam@evil.test\n2026-08-30 12:00:00 WARN  Admin role granted");
        when(jwtUtil.validateToken(anyString(), any())).thenReturn(true);
        when(jwtUtil.extractRoles(anyString())).thenReturn(List.of("ROLE_USER"));
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(principal);

        filter.doFilter(bearerRequest("a.b.c"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(lines(log)).isNotEmpty();
        assertThat(lines(log)).allSatisfy(line ->
                assertThat(line).doesNotContain("\n").doesNotContain("\r"));
        assertThat(lines(log)).noneMatch(line -> line.contains("sam@evil.test"));
    }

    // ------------------------------------------------------------------
    // CustomUserDetailsService — called by the filter on every request
    // ------------------------------------------------------------------

    /**
     * Once the row is in hand there is an id to name the user by, so the "found" line logs
     * that instead. The two lines that run before or without a row — the entry line and the
     * miss — have no id available and are masked.
     *
     * <p>Both surviving lines also drop from INFO to DEBUG. They are not a level change made
     * for its own sake: this service is called by {@link JwtRequestFilter} on every
     * authenticated request, so at INFO it emitted two lines per request purely to say that
     * a routine lookup happened. That is the per-request volume concern, and DEBUG is where
     * the filter's own equivalents already sat.</p>
     */
    @Test
    void theUserDetailsLookupNamesTheUserByIdAndMasksTheAddress() {
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser()));
        ListAppender<ILoggingEvent> log = capture(CustomUserDetailsService.class);

        UserDetails details = new CustomUserDetailsService(users).loadUserByUsername(EMAIL);

        assertThat(details.getUsername()).isEqualTo(EMAIL);
        assertNoAddress(lines(log));
        assertThat(lines(log)).anyMatch(line -> line.contains("Loading user by email: " + MASKED));
        assertThat(lines(log)).anyMatch(line -> line.contains("User loaded with id: " + USER_ID));
        assertThat(log.list).allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.DEBUG));
    }

    @Test
    void theUserDetailsLookupMasksTheAddressOfAnUnknownUser() {
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        ListAppender<ILoggingEvent> log = capture(CustomUserDetailsService.class);
        CustomUserDetailsService service = new CustomUserDetailsService(users);

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.security.core.userdetails.UsernameNotFoundException.class,
                () -> service.loadUserByUsername(EMAIL))).isNotNull();

        assertNoAddress(lines(log));
        assertThat(lines(log)).anyMatch(line -> line.contains("User not found with email: " + MASKED));
    }

    // ------------------------------------------------------------------
    // Services that hold a persisted user — the id replaces the address
    // ------------------------------------------------------------------

    private LabelServiceImpl labelService;
    private LabelRepository labelRepository;
    private TaskRepository taskRepository;
    private UserRepository userRepository;

    private void setUpLabelService() {
        labelRepository = mock(LabelRepository.class);
        taskRepository = mock(TaskRepository.class);
        userRepository = mock(UserRepository.class);
        labelService = new LabelServiceImpl(labelRepository, taskRepository, userRepository,
                mock(ActivityLogService.class));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser()));
    }

    private static Label label() {
        Label label = new Label();
        label.setId(7L);
        label.setName("urgent");
        label.setColor("#ff0000");
        return label;
    }

    @Test
    void everyLabelServiceStatementNamesTheUserByIdRatherThanByAddress() {
        setUpLabelService();
        Authentication authentication = authenticationFor(EMAIL);
        when(labelRepository.findByUser(any())).thenReturn(List.of(label()));
        when(labelRepository.existsByNameAndUser(anyString(), any())).thenReturn(false);
        when(labelRepository.save(any())).thenReturn(label());
        when(labelRepository.findByIdAndUser(eq(7L), any())).thenReturn(Optional.of(label()));
        Task task = task(persistedUser());
        when(taskRepository.findByIdAndUser(anyString(), any())).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenReturn(task);

        LabelDTO dto = new LabelDTO();
        dto.setName("urgent");
        dto.setColor("#ff0000");

        ListAppender<ILoggingEvent> log = capture(LabelServiceImpl.class);
        labelService.getMyLabels(authentication);
        labelService.createLabel(dto, authentication);
        labelService.updateLabel(7L, dto, authentication);
        labelService.addLabelToTask("TASK-1", 7L, authentication);
        labelService.removeLabelFromTask("TASK-1", 7L, authentication);
        labelService.deleteLabel(7L, authentication);

        assertNoAddress(lines(log));
        // Six entry statements, each naming the user by id.
        assertThat(lines(log).stream().filter(line -> line.contains("user id: " + USER_ID)).toList())
                .hasSize(7);
    }

    @Test
    void commentServiceNamesTheAuthorByIdAndMasksWhereNoUserIsResolved() {
        CommentRepository comments = mock(CommentRepository.class);
        TaskRepository tasks = mock(TaskRepository.class);
        UserRepository users = mock(UserRepository.class);
        CommentServiceImpl service =
                new CommentServiceImpl(comments, tasks, users, mock(ActivityLogService.class));

        User user = persistedUser();
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        Task task = task(user);
        when(tasks.findByIdAndUser(anyString(), any())).thenReturn(Optional.of(task));
        Comment comment = new Comment();
        comment.setId(11L);
        comment.setBody("first");
        comment.setAuthor(user);
        comment.setTask(task);
        when(comments.save(any())).thenReturn(comment);
        when(comments.findByIdAndTask(eq(11L), any())).thenReturn(Optional.of(comment));
        when(comments.findByTaskOrderByCreatedAtDesc(any())).thenReturn(List.of(comment));

        CommentDTO dto = new CommentDTO();
        dto.setBody("first");
        Authentication authentication = authenticationFor(EMAIL);

        ListAppender<ILoggingEvent> log = capture(CommentServiceImpl.class);
        service.getComments("TASK-1", authentication);
        service.addComment("TASK-1", dto, authentication);
        service.updateComment("TASK-1", 11L, dto, authentication);
        service.deleteComment("TASK-1", 11L, authentication);

        assertNoAddress(lines(log));
        assertThat(lines(log).stream().filter(line -> line.contains("by user id: " + USER_ID)).toList())
                .hasSize(3);
        // getComments resolves no user of its own -- getTaskForUser does its own lookup -- so
        // it masks rather than issuing a second query just to name the user by id.
        assertThat(lines(log)).anyMatch(line ->
                line.contains("Fetching comments for task") && line.contains(MASKED));
    }

    @Test
    void subtaskServiceNamesTheUserByIdAndMasksWhereNoUserIsResolved() {
        SubtaskRepository subtasks = mock(SubtaskRepository.class);
        TaskRepository tasks = mock(TaskRepository.class);
        UserRepository users = mock(UserRepository.class);
        SubtaskServiceImpl service =
                new SubtaskServiceImpl(subtasks, tasks, users, mock(ActivityLogService.class));

        User user = persistedUser();
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        Task task = task(user);
        when(tasks.findByIdAndUser(anyString(), any())).thenReturn(Optional.of(task));
        Subtask subtask = new Subtask();
        subtask.setId(3L);
        subtask.setTitle("step one");
        subtask.setTask(task);
        when(subtasks.save(any())).thenReturn(subtask);
        when(subtasks.countByTask(any())).thenReturn(0);
        when(subtasks.findByIdAndTask(eq(3L), any())).thenReturn(Optional.of(subtask));
        when(subtasks.findByTaskOrderByPositionAsc(any())).thenReturn(List.of(subtask));

        SubtaskDTO dto = new SubtaskDTO();
        dto.setTitle("step one");
        Authentication authentication = authenticationFor(EMAIL);

        ListAppender<ILoggingEvent> log = capture(SubtaskServiceImpl.class);
        service.getSubtasks("TASK-1", authentication);
        service.createSubtask("TASK-1", dto, authentication);
        service.updateSubtask("TASK-1", 3L, dto, authentication);
        service.deleteSubtask("TASK-1", 3L, authentication);

        assertNoAddress(lines(log));
        assertThat(lines(log).stream().filter(line -> line.contains("by user id: " + USER_ID)).toList())
                .hasSize(2);
        // The two read/delete entry lines resolve no user of their own, so they mask.
        assertThat(lines(log).stream().filter(line -> line.contains(MASKED)).toList()).hasSize(2);
    }

    /** Was written raw — neither masked nor sanitised — and the owner was persisted already. */
    @Test
    void projectCreationNamesTheOwnerById() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectMemberRepository members = mock(ProjectMemberRepository.class);
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser()));
        Project saved = new Project();
        saved.setId(9L);
        saved.setName("Sweep");
        saved.setOwner(persistedUser());
        when(projects.save(any())).thenReturn(saved);

        ProjectDTO dto = new ProjectDTO();
        dto.setName("Sweep");

        ListAppender<ILoggingEvent> log = capture(ProjectServiceImpl.class);
        new ProjectServiceImpl(projects, members, users).createProject(dto, authenticationFor(EMAIL));

        assertNoAddress(lines(log));
        assertThat(lines(log)).anyMatch(line -> line.contains("Project created: 9 by user id: " + USER_ID));
    }

    /**
     * Also raw. Worth pinning separately because this service writes a file as well as a log
     * line: the export must keep working, and the address must be absent from both.
     */
    @Test
    void theCsvExportNamesTheUserByIdAndPutsNoAddressInTheFile() throws Exception {
        TaskRepository tasks = mock(TaskRepository.class);
        UserRepository users = mock(UserRepository.class);
        User user = persistedUser();
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tasks.findByUser(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task(user))));

        ListAppender<ILoggingEvent> log = capture(TaskExportService.class);
        byte[] csv = new TaskExportService(tasks, users).exportTasksAsCsv(authenticationFor(EMAIL));

        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).contains("Ship the sweep");
        // The exported file carries task columns only -- there is no address column and never
        // was one -- so the leak here was the log line alone.
        assertThat(text).doesNotContain(EMAIL).doesNotContain("Email");

        assertNoAddress(lines(log));
        assertThat(lines(log)).anyMatch(line -> line.contains("Exported 1 tasks to CSV for user id: " + USER_ID));
    }

    /**
     * The one service statement that keeps a mask rather than an id: it sits ahead of both
     * the unauthenticated early return and the lookup, so there is no row to name yet, and
     * moving it below the lookup would silently drop the line on the unauthenticated path.
     */
    @Test
    void theTaskListingMasksTheAddressItCannotYetResolveToAnId() {
        TaskRepository tasks = mock(TaskRepository.class);
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser()));
        when(tasks.findByUser(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        TaskServiceImpl service = new TaskServiceImpl(tasks, users, mock(UniqueIdGenerator.class),
                mock(TaskConversionService.class), mock(ActivityLogService.class));

        ListAppender<ILoggingEvent> log = capture(TaskServiceImpl.class);
        Page<?> page = service.getAllTasks(PageRequest.of(0, 10), authenticationFor(EMAIL));

        assertThat(page).isNotNull();
        assertNoAddress(lines(log));
        assertThat(lines(log)).anyMatch(line -> line.contains("Retrieving all tasks for user: " + MASKED));
    }

    // ------------------------------------------------------------------
    // Controllers — only the Authentication is in hand, so these mask
    // ------------------------------------------------------------------

    /**
     * A controller holds the principal and nothing else. Resolving an id would mean a
     * repository call made solely to shorten a log line — and then the service below repeats
     * it — so these mask instead. The notification statements were raw as well as plaintext.
     */
    @Test
    void theNotificationControllerMasksTheAddressOnAllFourStatements() {
        NotificationServiceImpl service = mock(NotificationServiceImpl.class);
        when(service.getAllNotifications(any())).thenReturn(List.of());
        when(service.getUnreadNotifications(any())).thenReturn(List.of());
        NotificationController controller = new NotificationController(service);
        Authentication authentication = authenticationFor(EMAIL);

        ListAppender<ILoggingEvent> log = capture(NotificationController.class);
        controller.getAllNotifications(authentication);
        controller.getUnread(authentication);
        controller.markRead(5L, authentication);
        controller.markAllRead(authentication);

        assertNoAddress(lines(log));
        assertThat(lines(log)).hasSize(4).allMatch(line -> line.contains(MASKED));
    }

    @Test
    void theLabelControllerMasksTheAddressOnAllSixStatements() {
        LabelServiceImpl service = mock(LabelServiceImpl.class);
        when(service.getMyLabels(any())).thenReturn(List.of());
        when(service.createLabel(any(), any())).thenReturn(new LabelDTO());
        when(service.updateLabel(any(), any(), any())).thenReturn(new LabelDTO());
        when(service.addLabelToTask(anyString(), any(), any())).thenReturn(List.of());
        when(service.removeLabelFromTask(anyString(), any(), any())).thenReturn(List.of());
        LabelController controller = new LabelController(service);
        Authentication authentication = authenticationFor(EMAIL);
        LabelDTO dto = new LabelDTO();
        dto.setName("urgent");

        ListAppender<ILoggingEvent> log = capture(LabelController.class);
        controller.getMyLabels(authentication);
        controller.createLabel(dto, authentication);
        controller.updateLabel(1L, dto, authentication);
        controller.deleteLabel(1L, authentication);
        controller.addToTask("TASK-1", 1L, authentication);
        controller.removeFromTask("TASK-1", 1L, authentication);

        assertNoAddress(lines(log));
        assertThat(lines(log)).hasSize(6).allMatch(line -> line.contains(MASKED));
    }

    @Test
    void theCommentAndSubtaskControllersMaskTheAddress() {
        CommentServiceImpl commentService = mock(CommentServiceImpl.class);
        when(commentService.getComments(anyString(), any())).thenReturn(List.of());
        when(commentService.addComment(anyString(), any(), any())).thenReturn(new CommentDTO());
        when(commentService.updateComment(anyString(), any(), any(), any())).thenReturn(new CommentDTO());
        CommentController comments = new CommentController(commentService);

        SubtaskServiceImpl subtaskService = mock(SubtaskServiceImpl.class);
        when(subtaskService.getSubtasks(anyString(), any())).thenReturn(List.of());
        when(subtaskService.createSubtask(anyString(), any(), any())).thenReturn(new SubtaskDTO());
        when(subtaskService.updateSubtask(anyString(), any(), any(), any())).thenReturn(new SubtaskDTO());
        SubtaskController subtasks = new SubtaskController(subtaskService);

        Authentication authentication = authenticationFor(EMAIL);
        CommentDTO commentDto = new CommentDTO();
        commentDto.setBody("hi");
        SubtaskDTO subtaskDto = new SubtaskDTO();
        subtaskDto.setTitle("step");

        ListAppender<ILoggingEvent> commentLog = capture(CommentController.class);
        ListAppender<ILoggingEvent> subtaskLog = capture(SubtaskController.class);
        comments.getComments("TASK-1", authentication);
        comments.addComment("TASK-1", commentDto, authentication);
        comments.updateComment("TASK-1", 2L, commentDto, authentication);
        comments.deleteComment("TASK-1", 2L, authentication);
        subtasks.getSubtasks("TASK-1", authentication);
        subtasks.createSubtask("TASK-1", subtaskDto, authentication);
        subtasks.updateSubtask("TASK-1", 2L, subtaskDto, authentication);
        subtasks.deleteSubtask("TASK-1", 2L, authentication);

        assertNoAddress(lines(commentLog));
        assertNoAddress(lines(subtaskLog));
        // The GET statement in each names only the task, so three of the four lines mask.
        assertThat(lines(commentLog).stream().filter(line -> line.contains(MASKED)).toList()).hasSize(3);
        assertThat(lines(subtaskLog).stream().filter(line -> line.contains(MASKED)).toList()).hasSize(3);
    }

    /**
     * The avatar upload logged the principal twice. The second statement — the refusal of a
     * path that resolves outside the avatar directory — is masked alongside it, but is not
     * reachable to assert on: the filename is a generated UUID plus a constant extension, so
     * nothing the client sends can make the resolved path escape. It is masked for the same
     * reason the code keeps the check at all, as defence in depth.
     */
    @Test
    void theAvatarUploadMasksTheAddress() throws IOException {
        UserService userService = mock(UserService.class);
        User user = persistedUser();
        when(userService.getUserByEmail(EMAIL)).thenReturn(user);
        when(userService.convertToDTO(any())).thenReturn(new UserDTO());
        UserController controller = new UserController(userService, mock(PasswordResetService.class));
        Path uploads = Files.createTempDirectory("avatar-sweep");
        ReflectionTestUtils.setField(controller, "uploadDir", uploads.toString());

        MockMultipartFile file =
                new MockMultipartFile("file", "me.png", "image/png", "not-really-a-png".getBytes(StandardCharsets.UTF_8));

        ListAppender<ILoggingEvent> log = capture(UserController.class);
        controller.uploadAvatar(file, authenticationFor(EMAIL));

        assertNoAddress(lines(log));
        assertThat(lines(log)).anyMatch(line -> line.contains("Uploading avatar for user: " + MASKED));
    }

    // ------------------------------------------------------------------
    // The same leak by a second route: exception messages
    // ------------------------------------------------------------------

    /**
     * Masking the log statement is not enough on its own if the exception thrown on the very
     * next line interpolates the address into its message, because
     * {@link GlobalExceptionHandler} writes {@code ex.getMessage()} straight to the log —
     * unmasked and unsanitised — and, for these two handlers, into the response body as well.
     * That put the address back in plaintext one frame after it had been masked.
     *
     * <p>The address is dropped from the message rather than masked: the caller supplied it,
     * so echoing it back tells them nothing they did not already know, and the surviving
     * masked statements above already record which address was involved.</p>
     */
    @Test
    void theUserNotFoundMessageCarriesNoAddressIntoTheHandlerLog() {
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        UserServiceImpl service = new UserServiceImpl(users, mock(RoleRepository.class),
                mock(PasswordEncoder.class), mock(UserConversionService.class),
                mock(UserValidationService.class));

        UserNotFoundException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                UserNotFoundException.class, () -> service.getUserByEmail(EMAIL));
        assertThat(thrown.getMessage()).isEqualTo("User not found").doesNotContain(EMAIL);

        ListAppender<ILoggingEvent> log = capture(GlobalExceptionHandler.class);
        new GlobalExceptionHandler().handleUserNotFoundException(thrown, null);
        assertNoAddress(lines(log));
    }

    @Test
    void thePasswordResetMissMessageCarriesNoAddressEither() {
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        UserServiceImpl service = new UserServiceImpl(users, mock(RoleRepository.class),
                mock(PasswordEncoder.class), mock(UserConversionService.class),
                mock(UserValidationService.class));

        UserNotFoundException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                UserNotFoundException.class, () -> service.resetPassword(EMAIL, "NewPassw0rd!"));
        assertThat(thrown.getMessage()).doesNotContain(EMAIL);
    }

    /**
     * The login path's equivalent. This one is an {@code AuthenticationException}, so the
     * advice returns its message as the 401 body — the address was being handed to an
     * unauthenticated caller, which is a user-enumeration oracle as well as a log leak.
     */
    @Test
    void theUsernameNotFoundMessageCarriesNoAddress() {
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        CustomUserDetailsService service = new CustomUserDetailsService(users);

        Exception thrown = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.security.core.userdetails.UsernameNotFoundException.class,
                () -> service.loadUserByUsername(EMAIL));
        assertThat(thrown.getMessage()).isEqualTo("User not found").doesNotContain(EMAIL);
    }
}

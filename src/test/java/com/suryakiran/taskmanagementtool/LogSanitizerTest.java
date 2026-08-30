package com.suryakiran.taskmanagementtool;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.suryakiran.taskmanagementtool.controller.TaskController;
import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import com.suryakiran.taskmanagementtool.service.ActivityLogService;
import com.suryakiran.taskmanagementtool.service.TaskExportService;
import com.suryakiran.taskmanagementtool.service.TaskService;
import com.suryakiran.taskmanagementtool.util.LogSanitizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers {@code javasecurity:S5145} (log injection, CWE-117).
 *
 * <p>The logging was already parameterised, so a format string could not be injected. What
 * remained reachable was <em>log forging</em>: a newline inside a title, label name, comment
 * or email closes the current line and lets the caller write a further line that a log reader
 * cannot distinguish from a genuine one. {@link LogSanitizer} now collapses control characters
 * before the value is handed to SLF4J.</p>
 */
class LogSanitizerTest {

    // --- the sanitiser itself ---

    @Test
    void stripsLineFeedCarriageReturnAndTheOtherControlCharacters() {
        assertThat(LogSanitizer.sanitize("first\nsecond")).isEqualTo("first_second");
        assertThat(LogSanitizer.sanitize("first\rsecond")).isEqualTo("first_second");
        assertThat(LogSanitizer.sanitize("first\r\nsecond")).isEqualTo("first__second");
        assertThat(LogSanitizer.sanitize("col\tumn")).isEqualTo("col_umn");
        assertThat(LogSanitizer.sanitize("nul\u0000byte")).isEqualTo("nul_byte");
        assertThat(LogSanitizer.sanitize("esc\u001B[31mred")).isEqualTo("esc_[31mred");
        // Line separators a log viewer may honour even though they are not ASCII controls.
        assertThat(LogSanitizer.sanitize("a\u0085b\u2028c\u2029d")).isEqualTo("a_b_c_d");
    }

    /**
     * The forged-entry shape this rule is really about: everything after the newline is what
     * the attacker wanted the log to say. It has to end up on the one line.
     */
    @Test
    void aForgedLogEntryCollapsesOntoASingleLine() {
        String forged = "Buy milk\n2026-08-30 12:00:00 INFO  Admin role granted to attacker@evil.test";

        String sanitized = LogSanitizer.sanitize(forged);

        assertThat(sanitized).doesNotContain("\n").doesNotContain("\r");
        assertThat(sanitized.lines()).hasSize(1);
    }

    @Test
    void returnsAPlaceholderForNull() {
        assertThat(LogSanitizer.sanitize(null)).isEqualTo("<null>");
    }

    @Test
    void truncatesOverlongInputAndMarksIt() {
        String flood = "x".repeat(5_000);

        String sanitized = LogSanitizer.sanitize(flood);

        assertThat(sanitized).hasSize(LogSanitizer.MAX_LENGTH + LogSanitizer.TRUNCATION_MARKER.length());
        assertThat(sanitized).startsWith("x".repeat(LogSanitizer.MAX_LENGTH)).endsWith("...[truncated]");
    }

    @Test
    void leavesOrdinaryTextUnchanged() {
        assertThat(LogSanitizer.sanitize("Write the quarterly report")).isEqualTo("Write the quarterly report");
        assertThat(LogSanitizer.sanitize("owner@example.com")).isEqualTo("owner@example.com");
        assertThat(LogSanitizer.sanitize("Renovaci\u00f3n de p\u00f3liza \u2014 50%"))
                .isEqualTo("Renovaci\u00f3n de p\u00f3liza \u2014 50%");
        assertThat(LogSanitizer.sanitize("x".repeat(LogSanitizer.MAX_LENGTH)))
                .isEqualTo("x".repeat(LogSanitizer.MAX_LENGTH));
        assertThat(LogSanitizer.sanitize(42)).isEqualTo("42");
    }

    // --- end to end through a real log statement ---

    private ListAppender<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger controllerLogger;
    private TaskController taskController;

    @BeforeEach
    void attachAppender() {
        TaskService taskService = mock(TaskService.class);
        when(taskService.createTask(any(TaskDTO.class), any(Authentication.class))).thenReturn(new TaskDTO());
        taskController = new TaskController(taskService, mock(ActivityLogService.class),
                mock(TaskExportService.class));

        appender = new ListAppender<>();
        appender.start();
        controllerLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TaskController.class);
        controllerLogger.setLevel(Level.INFO);
        controllerLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        controllerLogger.detachAppender(appender);
    }

    /**
     * The attack as it arrives: a task title carrying a newline plus a plausible-looking
     * second entry. {@code POST /api/tasks} must still emit exactly one log line, and the
     * forged text must remain inside it rather than standing alone as its own record.
     */
    @Test
    void aForgedNewlineInATaskTitleCannotProduceASecondLogLine() {
        TaskDTO taskDTO = new TaskDTO();
        taskDTO.setTitle("Buy milk\nINFO  User attacker@evil.test granted ROLE_ADMIN");
        Authentication authentication =
                new UsernamePasswordAuthenticationToken("owner@example.com", null, List.of());

        taskController.createTask(taskDTO, authentication);

        assertThat(appender.list).hasSize(1);
        String rendered = appender.list.get(0).getFormattedMessage();
        assertThat(rendered).doesNotContain("\n").doesNotContain("\r");
        assertThat(rendered.lines()).hasSize(1);
        // The text is kept -- it is evidence -- but it is now visibly part of one entry.
        assertThat(rendered).isEqualTo(
                "Creating task: Buy milk_INFO  User attacker@evil.test granted ROLE_ADMIN");
    }
}

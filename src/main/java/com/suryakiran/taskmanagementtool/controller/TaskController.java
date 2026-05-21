package com.suryakiran.taskmanagementtool.controller;

import com.suryakiran.taskmanagementtool.dto.ActivityLogDTO;
import com.suryakiran.taskmanagementtool.dto.BulkTaskUpdateDTO;
import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import com.suryakiran.taskmanagementtool.dto.TaskStatsDTO;
import com.suryakiran.taskmanagementtool.model.Priority;
import com.suryakiran.taskmanagementtool.model.Status;
import com.suryakiran.taskmanagementtool.service.ActivityLogService;
import com.suryakiran.taskmanagementtool.service.TaskExportService;
import com.suryakiran.taskmanagementtool.service.TaskService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);

    private final TaskService taskService;
    private final ActivityLogService activityLogService;
    private final TaskExportService taskExportService;

    public TaskController(TaskService taskService, ActivityLogService activityLogService,
                          TaskExportService taskExportService) {
        this.taskService = taskService;
        this.activityLogService = activityLogService;
        this.taskExportService = taskExportService;
    }

    @GetMapping("/home")
    public String home() {
        return "Welcome to the Task Management Tool!";
    }

    @PostMapping
    public ResponseEntity<?> createTask(@Valid @RequestBody TaskDTO taskDTO, Authentication authentication) {
        logger.info("Creating task: {}", taskDTO.getTitle());
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        try {
            TaskDTO createdTask = taskService.createTask(taskDTO, authentication);
            return ResponseEntity.ok(createdTask);
        } catch (Exception e) {
            logger.error("Error creating task", e);
            return ResponseEntity.status(500).body("Failed to create task. Please try again later.");
        }
    }

    @GetMapping
    public ResponseEntity<List<TaskDTO>> getAllTasks(Pageable pageable, Authentication authentication) {
        Page<TaskDTO> taskPage = taskService.getAllTasks(pageable, authentication);
        return paginated(taskPage);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDTO> getTaskById(@PathVariable String id, Authentication authentication) {
        Optional<TaskDTO> taskDTO = taskService.getTaskById(id, authentication);
        return taskDTO.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ADMIN') or @taskService.isTaskOwner(#id, authentication?.principal?.id)")
    @PutMapping("/{id}")
    public ResponseEntity<TaskDTO> updateTask(@PathVariable String id,
                                              @Valid @RequestBody TaskDTO taskDTO,
                                              Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        TaskDTO updatedTask = taskService.updateTask(id, taskDTO, authentication);
        if (updatedTask == null) return ResponseEntity.status(404).build();
        return ResponseEntity.ok(updatedTask);
    }

    @PreAuthorize("hasRole('ADMIN') or @taskService.isTaskOwner(#id, authentication?.principal?.id)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable String id, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        taskService.deleteTask(id, authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/filter")
    public ResponseEntity<List<TaskDTO>> getTasks(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) Priority priority,
            Pageable pageable, Authentication authentication) {
        Page<TaskDTO> taskPage = taskService.getTasks(status, priority, pageable, authentication);
        return paginated(taskPage);
    }

    @GetMapping("/search")
    public ResponseEntity<List<TaskDTO>> searchTasks(@RequestParam String q, Pageable pageable,
                                                      Authentication authentication) {
        Page<TaskDTO> taskPage = taskService.searchTasks(q, pageable, authentication);
        return paginated(taskPage);
    }

    private static <T> ResponseEntity<List<T>> paginated(Page<T> page) {
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .header("X-Total-Pages", String.valueOf(page.getTotalPages()))
                .header("X-Page-Number", String.valueOf(page.getNumber()))
                .header("X-Page-Size", String.valueOf(page.getSize()))
                .header("Access-Control-Expose-Headers",
                        "X-Total-Count, X-Total-Pages, X-Page-Number, X-Page-Size")
                .body(page.getContent());
    }

    @GetMapping("/stats")
    public ResponseEntity<TaskStatsDTO> getTaskStats(Authentication authentication) {
        return ResponseEntity.ok(taskService.getTaskStats(authentication));
    }

    @PreAuthorize("hasRole('ADMIN') or @taskService.isTaskOwner(#id, authentication?.principal?.id)")
    @PostMapping("/{id}/restore")
    public ResponseEntity<TaskDTO> restoreTask(@PathVariable String id, Authentication authentication) {
        return ResponseEntity.ok(taskService.restoreTask(id, authentication));
    }

    @GetMapping("/{id}/activity")
    public ResponseEntity<List<ActivityLogDTO>> getTaskActivity(@PathVariable String id,
                                                                Authentication authentication) {
        return ResponseEntity.ok(activityLogService.getLogsForTask(id));
    }

    // Bulk operations
    @PostMapping("/bulk-update")
    public ResponseEntity<Map<String, Integer>> bulkUpdate(@Valid @RequestBody BulkTaskUpdateDTO dto,
                                                           Authentication authentication) {
        int updated = taskService.bulkUpdateTasks(dto.getTaskIds(), dto.getStatus(),
                dto.getPriority(), authentication);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping("/bulk-delete")
    public ResponseEntity<Map<String, Integer>> bulkDelete(@RequestBody BulkTaskUpdateDTO dto,
                                                            Authentication authentication) {
        int deleted = taskService.bulkDeleteTasks(dto.getTaskIds(), authentication);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    // CSV export
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TaskDTO>> getTasksForUser(@PathVariable int userId, Pageable pageable) {
        logger.info("Admin fetching tasks for user: {}", userId);
        return ResponseEntity.ok(taskService.getTasksForUser(userId, pageable).getContent());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(Authentication authentication) throws IOException {
        byte[] csv = taskExportService.exportTasksAsCsv(authentication);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tasks.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}

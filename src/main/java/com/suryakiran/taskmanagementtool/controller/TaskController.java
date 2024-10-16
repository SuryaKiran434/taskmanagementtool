package com.suryakiran.taskmanagementtool.controller;

import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import com.suryakiran.taskmanagementtool.model.Priority;
import com.suryakiran.taskmanagementtool.model.Status;
import com.suryakiran.taskmanagementtool.service.TaskService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/home")
    public String home() {
        logger.info("Accessed home endpoint");
        return "Welcome to the Task Management Tool!";
    }

    @PostMapping
    public ResponseEntity<TaskDTO> createTask(@Valid @RequestBody TaskDTO taskDTO, Authentication authentication) {
        logger.info("Creating task with title: {}", taskDTO.getTitle());
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        TaskDTO createdTask = taskService.createTask(taskDTO, authentication);
        logger.info("Task created with ID: {}", createdTask.getId());
        return ResponseEntity.ok(createdTask);
    }

    @GetMapping
    public ResponseEntity<List<TaskDTO>> getAllTasks(Pageable pageable, Authentication authentication) {
        logger.info("Retrieving all tasks");
        Page<TaskDTO> taskPage;
        if (authentication == null || !authentication.isAuthenticated()) {
            taskPage = taskService.getAllTasks(pageable);
        } else {
            taskPage = taskService.getAllTasks(pageable, authentication);
        }
        List<TaskDTO> tasks = taskPage.getContent();
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDTO> getTaskById(@PathVariable String id, Authentication authentication) {
        logger.info("Fetching task by ID: {}", id);
        Optional<TaskDTO> taskDTO;
        if (authentication == null || !authentication.isAuthenticated()) {
            taskDTO = taskService.getTaskById(id);
        } else {
            taskDTO = taskService.getTaskById(id, authentication);
        }
        return taskDTO.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') or @taskService.isTaskOwner(#id, authentication?.principal?.id)")
    @PutMapping("/{id}")
    public ResponseEntity<TaskDTO> updateTask(@PathVariable String id, @Valid @RequestBody TaskDTO taskDTO, Authentication authentication) {
        logger.info("Updating task with ID: {}", id);
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        TaskDTO updatedTask = taskService.updateTask(id, taskDTO, authentication);
        if(updatedTask == null) {
            return ResponseEntity.status(404).build();
        }
        logger.info("Task updated with ID: {}", id);
        return ResponseEntity.ok(updatedTask);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') or @taskService.isTaskOwner(#id, authentication?.principal?.id)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable String id, Authentication authentication) {
        logger.info("Deleting task with ID: {}", id);
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        taskService.deleteTask(id, authentication);
        logger.info("Task deleted with ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/filter")
    public ResponseEntity<List<TaskDTO>> getTasks(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) Priority priority,
            Pageable pageable, Authentication authentication) {
        logger.info("Filtering tasks with status: {} and priority: {}", status, priority);
        Page<TaskDTO> taskPage;
        if (authentication == null || !authentication.isAuthenticated()) {
            taskPage = taskService.getTasks(status, priority, pageable);
        } else {
            taskPage = taskService.getTasks(status, priority, pageable, authentication);
        }
        List<TaskDTO> tasks = taskPage.getContent();
        return ResponseEntity.ok(tasks);
    }
}
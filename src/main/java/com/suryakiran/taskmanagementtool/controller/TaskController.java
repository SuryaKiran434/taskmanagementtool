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
        TaskDTO createdTask = taskService.createTask(taskDTO, authentication);
        logger.info("Task created with ID: {}", createdTask.getId());
        return ResponseEntity.ok(createdTask);
    }

    @GetMapping
    public ResponseEntity<List<TaskDTO>> getAllTasks(Pageable pageable, Authentication authentication) {
        logger.info("Retrieving all tasks");
        Page<TaskDTO> taskPage = taskService.getAllTasks(pageable, authentication);
        List<TaskDTO> tasks = taskPage.getContent();
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDTO> getTaskById(@PathVariable String id, Authentication authentication) {
        logger.info("Fetching task by ID: {}", id);
        Optional<TaskDTO> taskDTO = taskService.getTaskById(id, authentication);
        if (taskDTO.isPresent()) {
            logger.info("Task found with ID: {}", id);
            return ResponseEntity.ok(taskDTO.get());
        } else {
            logger.warn("Task not found with ID: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    @PreAuthorize("hasRole('ADMIN') or @taskService.isTaskOwner(#id, authentication.principal.id)")
    @PutMapping("/{id}")
    public ResponseEntity<TaskDTO> updateTask(@PathVariable String id, @Valid @RequestBody TaskDTO taskDTO, Authentication authentication) {
        logger.info("Updating task with ID: {}", id);
        TaskDTO updatedTask = taskService.updateTask(id, taskDTO, authentication);
        logger.info("Task updated with ID: {}", id);
        return ResponseEntity.ok(updatedTask);
    }

    @PreAuthorize("hasRole('ADMIN') or @taskService.isTaskOwner(#id, authentication.principal.id)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable String id, Authentication authentication) {
        logger.info("Deleting task with ID: {}", id);
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
        Page<TaskDTO> taskPage = taskService.getTasks(status, priority, pageable, authentication);
        List<TaskDTO> tasks = taskPage.getContent();
        return ResponseEntity.ok(tasks);
    }
}
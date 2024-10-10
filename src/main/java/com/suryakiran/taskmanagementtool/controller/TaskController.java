package com.suryakiran.taskmanagementtool.controller;

import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import com.suryakiran.taskmanagementtool.model.Status;
import com.suryakiran.taskmanagementtool.model.Priority;
import com.suryakiran.taskmanagementtool.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
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
    public ResponseEntity<TaskDTO> createTask(@Valid @RequestBody TaskDTO taskDTO) {
        logger.info("Creating task with title: {}", taskDTO.getTitle());
        TaskDTO createdTask = taskService.createTask(taskDTO);
        return ResponseEntity.ok(createdTask);
    }

    @GetMapping
    public List<TaskDTO> getAllTasks() {
        logger.info("Retrieving all tasks");
        return taskService.getAllTasks();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDTO> getTaskById(@PathVariable String id) {
        logger.info("Fetching task by ID: {}", id);
        Optional<TaskDTO> taskDTO = taskService.getTaskById(id);
        return taskDTO.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskDTO> updateTask(@PathVariable String id, @Valid @RequestBody TaskDTO taskDTO) {
        logger.info("Updating task with id: {}", id);
        TaskDTO updatedTask = taskService.updateTask(id, taskDTO);
        return ResponseEntity.ok(updatedTask);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable String id) {
        logger.info("Deleting task with id: {}", id);
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/filter")
    public Page<TaskDTO> getTasks(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) Priority priority,
            Pageable pageable) {
        return taskService.getTasks(status, priority, pageable);
    }
}
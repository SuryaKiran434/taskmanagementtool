package com.suryakiran.taskmanagementtool.controller;

import com.suryakiran.taskmanagementtool.dto.SubtaskDTO;
import com.suryakiran.taskmanagementtool.service.SubtaskServiceImpl;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.suryakiran.taskmanagementtool.util.LogSanitizer.maskEmail;
import static com.suryakiran.taskmanagementtool.util.LogSanitizer.sanitize;

@RestController
@RequestMapping("/api/tasks/{taskId}/subtasks")
public class SubtaskController {

    private static final Logger logger = LoggerFactory.getLogger(SubtaskController.class);
    private final SubtaskServiceImpl subtaskService;

    public SubtaskController(SubtaskServiceImpl subtaskService) {
        this.subtaskService = subtaskService;
    }

    @GetMapping
    public ResponseEntity<List<SubtaskDTO>> getSubtasks(@PathVariable String taskId,
                                                         Authentication authentication) {
        logger.debug("GET subtasks for task: {}", sanitize(taskId));
        return ResponseEntity.ok(subtaskService.getSubtasks(taskId, authentication));
    }

    @PostMapping
    public ResponseEntity<SubtaskDTO> createSubtask(@PathVariable String taskId,
                                                     @Valid @RequestBody SubtaskDTO dto,
                                                     Authentication authentication) {
        logger.info("POST subtask on task: {} by: {}", sanitize(taskId), maskEmail(authentication.getName()));
        return ResponseEntity.ok(subtaskService.createSubtask(taskId, dto, authentication));
    }

    @PutMapping("/{subtaskId}")
    public ResponseEntity<SubtaskDTO> updateSubtask(@PathVariable String taskId,
                                                    @PathVariable Long subtaskId,
                                                    @Valid @RequestBody SubtaskDTO dto,
                                                    Authentication authentication) {
        logger.info("PUT subtask: {} on task: {} by: {}", subtaskId, sanitize(taskId),
                maskEmail(authentication.getName()));
        return ResponseEntity.ok(subtaskService.updateSubtask(taskId, subtaskId, dto, authentication));
    }

    @DeleteMapping("/{subtaskId}")
    public ResponseEntity<Void> deleteSubtask(@PathVariable String taskId,
                                              @PathVariable Long subtaskId,
                                              Authentication authentication) {
        logger.info("DELETE subtask: {} from task: {} by: {}", subtaskId, sanitize(taskId),
                maskEmail(authentication.getName()));
        subtaskService.deleteSubtask(taskId, subtaskId, authentication);
        return ResponseEntity.noContent().build();
    }
}

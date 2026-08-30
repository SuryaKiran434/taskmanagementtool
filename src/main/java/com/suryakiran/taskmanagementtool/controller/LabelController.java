package com.suryakiran.taskmanagementtool.controller;

import com.suryakiran.taskmanagementtool.dto.LabelDTO;
import com.suryakiran.taskmanagementtool.service.LabelServiceImpl;
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
@RequestMapping("/api/labels")
public class LabelController {

    private static final Logger logger = LoggerFactory.getLogger(LabelController.class);
    private final LabelServiceImpl labelService;

    public LabelController(LabelServiceImpl labelService) {
        this.labelService = labelService;
    }

    @GetMapping
    public ResponseEntity<List<LabelDTO>> getMyLabels(Authentication authentication) {
        logger.debug("GET labels for user: {}", maskEmail(authentication.getName()));
        return ResponseEntity.ok(labelService.getMyLabels(authentication));
    }

    @PostMapping
    public ResponseEntity<LabelDTO> createLabel(@Valid @RequestBody LabelDTO dto,
                                                 Authentication authentication) {
        logger.info("POST label '{}' by: {}", sanitize(dto.getName()), maskEmail(authentication.getName()));
        return ResponseEntity.ok(labelService.createLabel(dto, authentication));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LabelDTO> updateLabel(@PathVariable Long id,
                                                @Valid @RequestBody LabelDTO dto,
                                                Authentication authentication) {
        logger.info("PUT label: {} by: {}", id, maskEmail(authentication.getName()));
        return ResponseEntity.ok(labelService.updateLabel(id, dto, authentication));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLabel(@PathVariable Long id, Authentication authentication) {
        logger.info("DELETE label: {} by: {}", id, maskEmail(authentication.getName()));
        labelService.deleteLabel(id, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tasks/{taskId}/add/{labelId}")
    public ResponseEntity<List<LabelDTO>> addToTask(@PathVariable String taskId,
                                                    @PathVariable Long labelId,
                                                    Authentication authentication) {
        logger.info("Add label {} to task: {} by: {}", labelId, sanitize(taskId),
                maskEmail(authentication.getName()));
        return ResponseEntity.ok(labelService.addLabelToTask(taskId, labelId, authentication));
    }

    @DeleteMapping("/tasks/{taskId}/remove/{labelId}")
    public ResponseEntity<List<LabelDTO>> removeFromTask(@PathVariable String taskId,
                                                         @PathVariable Long labelId,
                                                         Authentication authentication) {
        logger.info("Remove label {} from task: {} by: {}", labelId, sanitize(taskId),
                maskEmail(authentication.getName()));
        return ResponseEntity.ok(labelService.removeLabelFromTask(taskId, labelId, authentication));
    }
}

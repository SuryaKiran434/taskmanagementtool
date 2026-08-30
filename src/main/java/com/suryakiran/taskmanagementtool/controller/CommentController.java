package com.suryakiran.taskmanagementtool.controller;

import com.suryakiran.taskmanagementtool.dto.CommentDTO;
import com.suryakiran.taskmanagementtool.service.CommentServiceImpl;
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
@RequestMapping("/api/tasks/{taskId}/comments")
public class CommentController {

    private static final Logger logger = LoggerFactory.getLogger(CommentController.class);
    private final CommentServiceImpl commentService;

    public CommentController(CommentServiceImpl commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public ResponseEntity<List<CommentDTO>> getComments(@PathVariable String taskId,
                                                        Authentication authentication) {
        logger.debug("GET comments for task: {}", sanitize(taskId));
        return ResponseEntity.ok(commentService.getComments(taskId, authentication));
    }

    @PostMapping
    public ResponseEntity<CommentDTO> addComment(@PathVariable String taskId,
                                                  @Valid @RequestBody CommentDTO dto,
                                                  Authentication authentication) {
        logger.info("POST comment on task: {} by: {}", sanitize(taskId), maskEmail(authentication.getName()));
        return ResponseEntity.ok(commentService.addComment(taskId, dto, authentication));
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<CommentDTO> updateComment(@PathVariable String taskId,
                                                    @PathVariable Long commentId,
                                                    @Valid @RequestBody CommentDTO dto,
                                                    Authentication authentication) {
        logger.info("PUT comment: {} on task: {} by: {}", commentId, sanitize(taskId),
                maskEmail(authentication.getName()));
        return ResponseEntity.ok(commentService.updateComment(taskId, commentId, dto, authentication));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable String taskId,
                                              @PathVariable Long commentId,
                                              Authentication authentication) {
        logger.info("DELETE comment: {} on task: {} by: {}", commentId, sanitize(taskId),
                maskEmail(authentication.getName()));
        commentService.deleteComment(taskId, commentId, authentication);
        return ResponseEntity.noContent().build();
    }
}

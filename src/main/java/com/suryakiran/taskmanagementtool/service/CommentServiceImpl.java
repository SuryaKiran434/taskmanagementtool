package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.CommentDTO;
import com.suryakiran.taskmanagementtool.exception.ResourceNotFoundException;
import com.suryakiran.taskmanagementtool.exception.TaskNotFoundException;
import com.suryakiran.taskmanagementtool.exception.UserNotFoundException;
import com.suryakiran.taskmanagementtool.model.*;
import com.suryakiran.taskmanagementtool.repository.CommentRepository;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.suryakiran.taskmanagementtool.util.LogSanitizer.maskEmail;
import static com.suryakiran.taskmanagementtool.util.LogSanitizer.sanitize;

@Service
public class CommentServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(CommentServiceImpl.class);

    private final CommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    public CommentServiceImpl(CommentRepository commentRepository, TaskRepository taskRepository,
                               UserRepository userRepository, ActivityLogService activityLogService) {
        this.commentRepository = commentRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
    }

    public List<CommentDTO> getComments(String taskId, Authentication authentication) {
        logger.debug("Fetching comments for task: {} by user: {}", sanitize(taskId),
                maskEmail(authentication.getName()));
        Task task = getTaskForUser(taskId, authentication);
        return commentRepository.findByTaskOrderByCreatedAtDesc(task).stream().map(this::toDTO).toList();
    }

    @Transactional
    public CommentDTO addComment(String taskId, CommentDTO dto, Authentication authentication) {
        User user = getUser(authentication.getName());
        logger.info("Adding comment to task: {} by user id: {}", sanitize(taskId), user.getId());
        Task task = getTaskForUser(taskId, authentication);
        Comment comment = new Comment();
        comment.setTask(task);
        comment.setAuthor(user);
        comment.setBody(dto.getBody());
        Comment saved = commentRepository.save(comment);
        activityLogService.log(taskId, user, ActivityAction.COMMENT_ADDED, null, null, "Comment added");
        logger.info("Comment {} added to task: {}", saved.getId(), sanitize(taskId));
        return toDTO(saved);
    }

    @Transactional
    public CommentDTO updateComment(String taskId, Long commentId, CommentDTO dto, Authentication authentication) {
        User user = getUser(authentication.getName());
        logger.info("Updating comment: {} on task: {} by user id: {}", commentId, sanitize(taskId),
                user.getId());
        Task task = getTaskForUser(taskId, authentication);
        Comment comment = commentRepository.findByIdAndTask(commentId, task)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && comment.getAuthor().getId() != user.getId()) {
            logger.warn("User {} attempted to edit comment {} owned by user {}",
                    user.getId(), commentId, comment.getAuthor().getId());
            throw new org.springframework.security.access.AccessDeniedException("Cannot edit another user's comment");
        }
        comment.setBody(dto.getBody());
        return toDTO(commentRepository.save(comment));
    }

    @Transactional
    public void deleteComment(String taskId, Long commentId, Authentication authentication) {
        User user = getUser(authentication.getName());
        logger.info("Deleting comment: {} on task: {} by user id: {}", commentId, sanitize(taskId),
                user.getId());
        Task task = getTaskForUser(taskId, authentication);
        Comment comment = commentRepository.findByIdAndTask(commentId, task)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && comment.getAuthor().getId() != user.getId()) {
            logger.warn("User {} attempted to delete comment {} owned by user {}",
                    user.getId(), commentId, comment.getAuthor().getId());
            throw new org.springframework.security.access.AccessDeniedException("Cannot delete another user's comment");
        }
        commentRepository.delete(comment);
        activityLogService.log(taskId, user, ActivityAction.COMMENT_DELETED, null, null, "Comment deleted");
        logger.info("Comment {} deleted from task: {}", commentId, sanitize(taskId));
    }

    private Task getTaskForUser(String taskId, Authentication authentication) {
        User user = getUser(authentication.getName());
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return taskRepository.findById(taskId)
                    .orElseThrow(() -> new TaskNotFoundException("Task not found"));
        }
        return taskRepository.findByIdAndUser(taskId, user)
                .orElseThrow(() -> new TaskNotFoundException("Task not found"));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private CommentDTO toDTO(Comment comment) {
        CommentDTO dto = new CommentDTO();
        dto.setId(comment.getId());
        dto.setBody(comment.getBody());
        dto.setCreatedAt(comment.getCreatedAt());
        dto.setUpdatedAt(comment.getUpdatedAt());
        if (comment.getAuthor() != null) {
            dto.setAuthorId(comment.getAuthor().getId());
            dto.setAuthorName(comment.getAuthor().getFirstName() + " " + comment.getAuthor().getLastName());
            dto.setAuthorAvatarUrl(comment.getAuthor().getAvatarUrl());
        }
        return dto;
    }
}

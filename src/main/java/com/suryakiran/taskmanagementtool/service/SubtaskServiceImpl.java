package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.SubtaskDTO;
import com.suryakiran.taskmanagementtool.exception.ResourceNotFoundException;
import com.suryakiran.taskmanagementtool.exception.TaskNotFoundException;
import com.suryakiran.taskmanagementtool.exception.UserNotFoundException;
import com.suryakiran.taskmanagementtool.model.*;
import com.suryakiran.taskmanagementtool.repository.SubtaskRepository;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SubtaskServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(SubtaskServiceImpl.class);

    private final SubtaskRepository subtaskRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    public SubtaskServiceImpl(SubtaskRepository subtaskRepository, TaskRepository taskRepository,
                               UserRepository userRepository, ActivityLogService activityLogService) {
        this.subtaskRepository = subtaskRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
    }

    public List<SubtaskDTO> getSubtasks(String taskId, Authentication authentication) {
        logger.debug("Fetching subtasks for task: {} by user: {}", taskId, authentication.getName());
        Task task = getTaskForUser(taskId, authentication);
        return subtaskRepository.findByTaskOrderByPositionAsc(task).stream().map(this::toDTO).toList();
    }

    @Transactional
    public SubtaskDTO createSubtask(String taskId, SubtaskDTO dto, Authentication authentication) {
        User user = getUser(authentication.getName());
        logger.info("Creating subtask '{}' on task: {} by user: {}", dto.getTitle(), taskId, user.getEmail());
        Task task = getTaskForUser(taskId, authentication);
        Subtask subtask = new Subtask();
        subtask.setTask(task);
        subtask.setTitle(dto.getTitle());
        subtask.setCompleted(false);
        subtask.setPosition(subtaskRepository.countByTask(task));
        Subtask saved = subtaskRepository.save(subtask);
        activityLogService.log(taskId, user, ActivityAction.SUBTASK_ADDED,
                null, saved.getTitle(), "Subtask added");
        logger.info("Subtask {} created on task: {}", saved.getId(), taskId);
        return toDTO(saved);
    }

    @Transactional
    public SubtaskDTO updateSubtask(String taskId, Long subtaskId, SubtaskDTO dto, Authentication authentication) {
        User user = getUser(authentication.getName());
        logger.info("Updating subtask: {} on task: {} by user: {}", subtaskId, taskId, user.getEmail());
        Task task = getTaskForUser(taskId, authentication);
        Subtask subtask = subtaskRepository.findByIdAndTask(subtaskId, task)
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found"));

        boolean wasCompleted = subtask.isCompleted();
        subtask.setTitle(dto.getTitle());
        subtask.setCompleted(dto.isCompleted());
        subtask.setPosition(dto.getPosition());
        Subtask saved = subtaskRepository.save(subtask);

        if (!wasCompleted && saved.isCompleted()) {
            logger.info("Subtask {} completed on task: {}", subtaskId, taskId);
            activityLogService.log(taskId, user, ActivityAction.SUBTASK_COMPLETED,
                    null, saved.getTitle(), "Subtask completed");
        }
        return toDTO(saved);
    }

    @Transactional
    public void deleteSubtask(String taskId, Long subtaskId, Authentication authentication) {
        logger.info("Deleting subtask: {} from task: {} by user: {}", subtaskId, taskId, authentication.getName());
        Task task = getTaskForUser(taskId, authentication);
        Subtask subtask = subtaskRepository.findByIdAndTask(subtaskId, task)
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found"));
        subtaskRepository.delete(subtask);
        logger.info("Subtask {} deleted from task: {}", subtaskId, taskId);
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

    private SubtaskDTO toDTO(Subtask subtask) {
        SubtaskDTO dto = new SubtaskDTO();
        dto.setId(subtask.getId());
        dto.setTitle(subtask.getTitle());
        dto.setCompleted(subtask.isCompleted());
        dto.setPosition(subtask.getPosition());
        dto.setCreatedAt(subtask.getCreatedAt());
        return dto;
    }
}

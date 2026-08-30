package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.LabelDTO;
import com.suryakiran.taskmanagementtool.exception.ResourceNotFoundException;
import com.suryakiran.taskmanagementtool.exception.TaskNotFoundException;
import com.suryakiran.taskmanagementtool.exception.UserNotFoundException;
import com.suryakiran.taskmanagementtool.model.*;
import com.suryakiran.taskmanagementtool.repository.LabelRepository;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.suryakiran.taskmanagementtool.util.LogSanitizer.sanitize;

@Service
public class LabelServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(LabelServiceImpl.class);

    private final LabelRepository labelRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    public LabelServiceImpl(LabelRepository labelRepository, TaskRepository taskRepository,
                             UserRepository userRepository, ActivityLogService activityLogService) {
        this.labelRepository = labelRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
    }

    public List<LabelDTO> getMyLabels(Authentication authentication) {
        logger.debug("Fetching labels for user: {}", sanitize(authentication.getName()));
        User user = getUser(authentication.getName());
        return labelRepository.findByUser(user).stream().map(this::toDTO).toList();
    }

    @Transactional
    public LabelDTO createLabel(LabelDTO dto, Authentication authentication) {
        User user = getUser(authentication.getName());
        logger.info("Creating label '{}' for user: {}", sanitize(dto.getName()), sanitize(user.getEmail()));
        if (labelRepository.existsByNameAndUser(dto.getName(), user)) {
            throw new IllegalArgumentException("Label with this name already exists");
        }
        Label label = new Label();
        label.setName(dto.getName());
        label.setColor(dto.getColor());
        label.setUser(user);
        LabelDTO created = toDTO(labelRepository.save(label));
        logger.info("Label {} created for user: {}", created.getId(), sanitize(user.getEmail()));
        return created;
    }

    @Transactional
    public LabelDTO updateLabel(Long id, LabelDTO dto, Authentication authentication) {
        User user = getUser(authentication.getName());
        logger.info("Updating label: {} for user: {}", id, sanitize(user.getEmail()));
        Label label = labelRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Label not found"));
        label.setName(dto.getName());
        label.setColor(dto.getColor());
        return toDTO(labelRepository.save(label));
    }

    @Transactional
    public void deleteLabel(Long id, Authentication authentication) {
        User user = getUser(authentication.getName());
        logger.info("Deleting label: {} for user: {}", id, sanitize(user.getEmail()));
        Label label = labelRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Label not found"));
        labelRepository.delete(label);
        logger.info("Label {} deleted", id);
    }

    @Transactional
    public List<LabelDTO> addLabelToTask(String taskId, Long labelId, Authentication authentication) {
        User user = getUser(authentication.getName());
        logger.info("Adding label: {} to task: {} by user: {}", labelId, sanitize(taskId),
                sanitize(user.getEmail()));
        Task task = taskRepository.findByIdAndUser(taskId, user)
                .orElseThrow(() -> new TaskNotFoundException("Task not found"));
        Label label = labelRepository.findByIdAndUser(labelId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Label not found"));
        task.getLabels().add(label);
        taskRepository.save(task);
        activityLogService.log(taskId, user, ActivityAction.LABEL_ADDED,
                null, label.getName(), "Label added to task");
        return task.getLabels().stream().map(this::toDTO).toList();
    }

    @Transactional
    public List<LabelDTO> removeLabelFromTask(String taskId, Long labelId, Authentication authentication) {
        User user = getUser(authentication.getName());
        logger.info("Removing label: {} from task: {} by user: {}", labelId, sanitize(taskId),
                sanitize(user.getEmail()));
        Task task = taskRepository.findByIdAndUser(taskId, user)
                .orElseThrow(() -> new TaskNotFoundException("Task not found"));
        Label label = labelRepository.findByIdAndUser(labelId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Label not found"));
        task.getLabels().remove(label);
        taskRepository.save(task);
        activityLogService.log(taskId, user, ActivityAction.LABEL_REMOVED,
                label.getName(), null, "Label removed from task");
        return task.getLabels().stream().map(this::toDTO).toList();
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private LabelDTO toDTO(Label label) {
        LabelDTO dto = new LabelDTO();
        dto.setId(label.getId());
        dto.setName(label.getName());
        dto.setColor(label.getColor());
        return dto;
    }
}

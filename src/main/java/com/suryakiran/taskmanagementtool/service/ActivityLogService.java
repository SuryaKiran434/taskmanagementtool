package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.ActivityLogDTO;
import com.suryakiran.taskmanagementtool.model.ActivityAction;
import com.suryakiran.taskmanagementtool.model.ActivityLog;
import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.repository.ActivityLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ActivityLogService {

    private static final Logger logger = LoggerFactory.getLogger(ActivityLogService.class);

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String taskId, User user, ActivityAction action, String oldValue, String newValue, String description) {
        try {
            ActivityLog log = new ActivityLog();
            log.setTaskId(taskId);
            log.setUser(user);
            log.setAction(action);
            log.setOldValue(oldValue);
            log.setNewValue(newValue);
            log.setDescription(description);
            activityLogRepository.save(log);
        } catch (Exception e) {
            logger.error("Failed to save activity log for task {}: {}", taskId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ActivityLogDTO> getLogsForTask(String taskId) {
        return activityLogRepository.findByTaskIdOrderByCreatedAtDesc(taskId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    private ActivityLogDTO toDTO(ActivityLog log) {
        ActivityLogDTO dto = new ActivityLogDTO();
        dto.setId(log.getId());
        dto.setTaskId(log.getTaskId());
        dto.setAction(log.getAction());
        dto.setOldValue(log.getOldValue());
        dto.setNewValue(log.getNewValue());
        dto.setDescription(log.getDescription());
        dto.setCreatedAt(log.getCreatedAt());
        if (log.getUser() != null) {
            dto.setUserId(log.getUser().getId());
            dto.setUserName(log.getUser().getFirstName() + " " + log.getUser().getLastName());
            dto.setUserAvatarUrl(log.getUser().getAvatarUrl());
        }
        return dto;
    }
}

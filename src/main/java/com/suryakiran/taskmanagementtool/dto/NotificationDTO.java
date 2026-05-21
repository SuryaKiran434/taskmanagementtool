package com.suryakiran.taskmanagementtool.dto;

import com.suryakiran.taskmanagementtool.model.NotificationType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class NotificationDTO {

    private Long id;
    private NotificationType type;
    private String message;
    private boolean read;
    private String taskId;
    private Instant createdAt;
}

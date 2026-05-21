package com.suryakiran.taskmanagementtool.dto;

import com.suryakiran.taskmanagementtool.model.ActivityAction;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class ActivityLogDTO {

    private Long id;
    private String taskId;
    private int userId;
    private String userName;
    private String userAvatarUrl;
    private ActivityAction action;
    private String oldValue;
    private String newValue;
    private String description;
    private Instant createdAt;
}

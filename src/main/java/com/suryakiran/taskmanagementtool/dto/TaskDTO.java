package com.suryakiran.taskmanagementtool.dto;

import com.suryakiran.taskmanagementtool.model.Status;
import com.suryakiran.taskmanagementtool.model.Priority;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskDTO {
    private String id;
    private String title;
    private String description;
    private Status status;
    private Priority priority;
}
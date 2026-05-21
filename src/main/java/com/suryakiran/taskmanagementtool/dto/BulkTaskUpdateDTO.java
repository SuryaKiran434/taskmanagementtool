package com.suryakiran.taskmanagementtool.dto;

import com.suryakiran.taskmanagementtool.model.Priority;
import com.suryakiran.taskmanagementtool.model.Status;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BulkTaskUpdateDTO {

    @NotEmpty(message = "Task IDs are required")
    private List<String> taskIds;

    private Status status;
    private Priority priority;
}

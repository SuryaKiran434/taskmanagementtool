package com.suryakiran.taskmanagementtool.dto;

import com.suryakiran.taskmanagementtool.model.Status;
import com.suryakiran.taskmanagementtool.model.Priority;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class TaskDTO {

    private String id;

    @NotBlank(message = "Title is mandatory")
    @Size(max = 100, message = "Title must be less than 100 characters")
    private String title;

    @NotBlank(message = "Description is mandatory")
    @Size(max = 1000, message = "Description must be less than 1000 characters")
    private String description;

    @NotNull(message = "Status is mandatory")
    private Status status;

    @NotNull(message = "Priority is mandatory")
    private Priority priority;

    @NotNull(message = "Due date is mandatory")
    private LocalDate dueDate;

    private UserDTO creator;
    private String creatorFirstName;
    private String creatorLastName;

    private Integer assigneeId;
    private String assigneeFirstName;
    private String assigneeLastName;
    private String assigneeEmail;
    private String assigneeAvatarUrl;

    private String category;

    private Instant createdAt;
    private Instant updatedAt;

    private Long projectId;
    private String projectName;

    private int subtaskCount;
    private int completedSubtaskCount;

    private List<LabelDTO> labels;

    public TaskDTO() {
    }
}

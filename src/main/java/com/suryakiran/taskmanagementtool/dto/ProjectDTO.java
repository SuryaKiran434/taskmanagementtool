package com.suryakiran.taskmanagementtool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class ProjectDTO {

    private Long id;

    @NotBlank(message = "Project name is required")
    @Size(max = 255)
    private String name;

    @Size(max = 1000)
    private String description;

    private String color;

    private int ownerId;
    private String ownerName;

    private Instant createdAt;
    private Instant updatedAt;

    private int taskCount;
    private List<ProjectMemberDTO> members;
}

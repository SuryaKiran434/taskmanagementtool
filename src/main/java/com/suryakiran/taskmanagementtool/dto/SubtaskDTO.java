package com.suryakiran.taskmanagementtool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class SubtaskDTO {

    private Long id;

    @NotBlank(message = "Subtask title is required")
    @Size(max = 255)
    private String title;

    private boolean completed;
    private int position;
    private Instant createdAt;
}

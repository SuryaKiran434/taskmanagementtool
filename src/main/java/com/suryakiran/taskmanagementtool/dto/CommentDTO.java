package com.suryakiran.taskmanagementtool.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class CommentDTO {

    private Long id;

    @NotBlank(message = "Comment body is required")
    private String body;

    private int authorId;
    private String authorName;
    private String authorAvatarUrl;

    private Instant createdAt;
    private Instant updatedAt;
}

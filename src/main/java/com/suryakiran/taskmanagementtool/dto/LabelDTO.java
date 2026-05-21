package com.suryakiran.taskmanagementtool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LabelDTO {

    private Long id;

    @NotBlank(message = "Label name is required")
    @Size(max = 100)
    private String name;

    private String color;
}

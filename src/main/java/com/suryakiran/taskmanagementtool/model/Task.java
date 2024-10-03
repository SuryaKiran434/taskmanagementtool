package com.suryakiran.taskmanagementtool.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "task")
public class Task {
    @Id
    private String id;
    @NotEmpty(message = "Title is required")
    private String title;// Updated field name
    @NotEmpty(message = "Description is required")
    private String description;

    public Task() {}

    public Task(String id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }
}
package com.suryakiran.taskmanagementtool.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "subtask", indexes = {
        @Index(name = "idx_subtask_task_id", columnList = "task_id")
})
public class Subtask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @NotBlank(message = "Subtask title is required")
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "completed", nullable = false)
    private boolean completed = false;

    @Column(name = "position", nullable = false)
    private int position = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}

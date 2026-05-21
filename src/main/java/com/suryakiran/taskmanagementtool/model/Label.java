package com.suryakiran.taskmanagementtool.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "label", indexes = {
        @Index(name = "idx_label_user_id", columnList = "user_id")
})
public class Label {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Label name is required")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "color", length = 20)
    private String color;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JsonIgnore
    @ManyToMany(mappedBy = "labels", fetch = FetchType.LAZY)
    private Set<Task> tasks = new HashSet<>();
}

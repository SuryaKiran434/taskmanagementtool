package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import com.suryakiran.taskmanagementtool.model.Status;
import com.suryakiran.taskmanagementtool.model.Priority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.util.Optional;

public interface TaskService {
    TaskDTO createTask(TaskDTO taskDTO, Authentication authentication);
    Page<TaskDTO> getAllTasks(Pageable pageable);
    Page<TaskDTO> getAllTasks(Pageable pageable, Authentication authentication);
    Optional<TaskDTO> getTaskById(String id);
    Optional<TaskDTO> getTaskById(String id, Authentication authentication);
    TaskDTO updateTask(String id, TaskDTO taskDTO, Authentication authentication);
    void deleteTask(String id, Authentication authentication);
    Page<TaskDTO> getTasks(Status status, Priority priority, Pageable pageable);
    Page<TaskDTO> getTasks(Status status, Priority priority, Pageable pageable, Authentication authentication);
    boolean isTaskOwner(String taskId, int userId);
}
package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import com.suryakiran.taskmanagementtool.model.Status;
import com.suryakiran.taskmanagementtool.model.Priority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface TaskService {
    TaskDTO createTask(TaskDTO taskDTO);
    List<TaskDTO> getAllTasks();
    Optional<TaskDTO> getTaskById(String id);
    TaskDTO updateTask(String id, TaskDTO taskDTO);
    void deleteTask(String id);
    Page<TaskDTO> getTasks(Status status, Priority priority, Pageable pageable);
}
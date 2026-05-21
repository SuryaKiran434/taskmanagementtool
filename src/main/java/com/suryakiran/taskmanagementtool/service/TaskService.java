package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import com.suryakiran.taskmanagementtool.dto.TaskStatsDTO;
import com.suryakiran.taskmanagementtool.model.Priority;
import com.suryakiran.taskmanagementtool.model.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.util.List;
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
    Page<TaskDTO> searchTasks(String query, Pageable pageable, Authentication authentication);
    TaskStatsDTO getTaskStats(Authentication authentication);
    TaskDTO restoreTask(String id, Authentication authentication);
    int bulkUpdateTasks(List<String> taskIds, Status status, Priority priority, Authentication authentication);
    int bulkDeleteTasks(List<String> taskIds, Authentication authentication);
    Page<TaskDTO> getTasksForUser(int userId, Pageable pageable);
}
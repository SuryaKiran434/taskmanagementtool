package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.model.Task;

import java.util.List;
import java.util.Optional;

public interface TaskService {
    Task createTask(Task task);
    List<Task> getAllTasks();
    Optional<Task> getTaskById(String id);
    Task updateTask(String id, Task taskDetails);
    void deleteTask(String id);
}
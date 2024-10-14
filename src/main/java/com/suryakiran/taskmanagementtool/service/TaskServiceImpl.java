package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import com.suryakiran.taskmanagementtool.exception.NoTasksFoundException;
import com.suryakiran.taskmanagementtool.exception.TaskNotFoundException;
import com.suryakiran.taskmanagementtool.model.Priority;
import com.suryakiran.taskmanagementtool.model.Status;
import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import com.suryakiran.taskmanagementtool.util.UniqueIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TaskServiceImpl implements TaskService {

    private static final Logger logger = LoggerFactory.getLogger(TaskServiceImpl.class);

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final UniqueIdGenerator uniqueIdGenerator;

    @Autowired
    public TaskServiceImpl(TaskRepository taskRepository, UserRepository userRepository, UniqueIdGenerator uniqueIdGenerator) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.uniqueIdGenerator = uniqueIdGenerator;
    }

    @Override
    public TaskDTO createTask(TaskDTO taskDTO, Authentication authentication) {
        logger.info("Creating task with title: {}", taskDTO.getTitle());
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Task task = convertToEntity(taskDTO);
        task.setId(uniqueIdGenerator.generateUniqueId());
        task.setUser(user);
        Task savedTask = taskRepository.save(task);
        logger.info("Task created with ID: {}", savedTask.getId());
        return convertToDTO(savedTask);
    }

    @Override
    public Page<TaskDTO> getAllTasks(Pageable pageable, Authentication authentication) {
        logger.info("Retrieving all tasks for user: {}", authentication.getName());
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Page<Task> tasks = taskRepository.findByUser(user, pageable);
        if (tasks.isEmpty()) {
            throw new NoTasksFoundException("No tasks found for user: " + user.getFirstName() + " " + user.getLastName());
        }
        return tasks.map(this::convertToDTO);
    }

    @Override
    public Optional<TaskDTO> getTaskById(String id, Authentication authentication) {
        logger.info("Fetching task by ID: {}", id);
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Optional<Task> task = taskRepository.findByIdAndUser(id, user);
        return task.map(this::convertToDTO);
    }

    @Override
    public TaskDTO updateTask(String id, TaskDTO taskDTO, Authentication authentication) {
        logger.info("Updating task with ID: {}", id);
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Task task = taskRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new TaskNotFoundException("Task not found"));
        task.setTitle(taskDTO.getTitle());
        task.setDescription(taskDTO.getDescription());
        task.setStatus(taskDTO.getStatus());
        task.setPriority(taskDTO.getPriority());
        Task updatedTask = taskRepository.save(task);
        logger.info("Task updated with ID: {}", updatedTask.getId());
        return convertToDTO(updatedTask);
    }

    @Override
    public void deleteTask(String id, Authentication authentication) {
        logger.info("Deleting task with ID: {}", id);
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Task task = taskRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new TaskNotFoundException("Task not found"));
        taskRepository.delete(task);
        logger.info("Task deleted with ID: {}", id);
    }

    @Override
    public Page<TaskDTO> getTasks(Status status, Priority priority, Pageable pageable, Authentication authentication) {
        logger.info("Filtering tasks with status: {} and priority: {}", status, priority);
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Page<Task> tasks = taskRepository.findByUserAndStatusAndPriority(user, status, priority, pageable);
        return tasks.map(this::convertToDTO);
    }

    @Override
    public boolean isTaskOwner(String taskId, int userId) {
        return taskRepository.existsByIdAndUserId(taskId, userId);
    }

    private TaskDTO convertToDTO(Task task) {
        TaskDTO taskDTO = new TaskDTO();
        taskDTO.setId(task.getId());
        taskDTO.setTitle(task.getTitle());
        taskDTO.setDescription(task.getDescription());
        taskDTO.setStatus(task.getStatus());
        taskDTO.setPriority(task.getPriority());
        return taskDTO;
    }

    private Task convertToEntity(TaskDTO taskDTO) {
        Task task = new Task();
        task.setId(taskDTO.getId());
        task.setTitle(taskDTO.getTitle());
        task.setDescription(taskDTO.getDescription());
        task.setStatus(taskDTO.getStatus());
        task.setPriority(taskDTO.getPriority());
        return task;
    }
}
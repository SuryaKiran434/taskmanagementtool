package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import com.suryakiran.taskmanagementtool.exception.AuthenticationRequiredException;
import com.suryakiran.taskmanagementtool.exception.NoTasksFoundException;
import com.suryakiran.taskmanagementtool.exception.TaskNotFoundException;
import com.suryakiran.taskmanagementtool.exception.UserNotFoundException;
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
    private static final String USER_NOT_FOUND = "User not found";
    private static final String AUTHENTICATION_REQUIRED = "Authentication required";

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
        if (!authentication.isAuthenticated()) {
            throw new AuthenticationRequiredException(AUTHENTICATION_REQUIRED);
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        Task task = convertToEntity(taskDTO);
        task.setId(uniqueIdGenerator.generateUniqueId());
        task.setUser(user);
        Task savedTask = taskRepository.save(task);
        logger.info("Task created with ID: {}", savedTask.getId());
        return convertToDTO(savedTask);
    }

    @Override
    public Page<TaskDTO> getAllTasks(Pageable pageable) {
        logger.info("Retrieving all tasks without authentication");
        Page<Task> tasks = taskRepository.findAll(pageable);
        if (tasks.isEmpty()) {
            throw new NoTasksFoundException("No tasks found");
        }
        return tasks.map(this::convertToDTO);
    }

    @Override
    public Page<TaskDTO> getAllTasks(Pageable pageable, Authentication authentication) {
        logger.info("Retrieving all tasks for user: {}", authentication.getName());
        if (!authentication.isAuthenticated()) {
            return getAllTasks(pageable);
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        Page<Task> tasks = taskRepository.findByUser(user, pageable);
        if (tasks.isEmpty()) {
            throw new NoTasksFoundException("No tasks found for user: " + user.getFirstName() + " " + user.getLastName());
        }
        return tasks.map(this::convertToDTO);
    }

    @Override
    public Optional<TaskDTO> getTaskById(String id) {
        logger.info("Fetching task by ID: {}", id);
        Optional<Task> task = taskRepository.findById(id);
        return task.map(this::convertToDTO);
    }

    @Override
    public Optional<TaskDTO> getTaskById(String id, Authentication authentication) {
        logger.info("Fetching task by ID: {}", id);
        if (!authentication.isAuthenticated()) {
            return getTaskById(id);
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        Optional<Task> task = taskRepository.findByIdAndUser(id, user);
        return task.map(this::convertToDTO);
    }

    @Override
    public TaskDTO updateTask(String id, TaskDTO taskDTO, Authentication authentication) {
        logger.info("Updating task with ID: {}", id);
        if (!authentication.isAuthenticated()) {
            throw new AuthenticationRequiredException(AUTHENTICATION_REQUIRED);
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
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
        if (!authentication.isAuthenticated()) {
            throw new AuthenticationRequiredException(AUTHENTICATION_REQUIRED);
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        Task task = taskRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new TaskNotFoundException("Task not found"));
        taskRepository.delete(task);
        logger.info("Task deleted with ID: {}", id);
    }

    @Override
    public Page<TaskDTO> getTasks(Status status, Priority priority, Pageable pageable) {
        logger.info("Filtering tasks with status: {} and priority: {}", status, priority);
        Page<Task> tasks = taskRepository.findByStatusOrPriority(status, priority, pageable);
        return tasks.map(this::convertToDTO);
    }

    @Override
    public Page<TaskDTO> getTasks(Status status, Priority priority, Pageable pageable, Authentication authentication) {
        logger.info("Filtering tasks with status: {} and priority: {}", status, priority);
        if (!authentication.isAuthenticated()) {
            return getTasks(status, priority, pageable);
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
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
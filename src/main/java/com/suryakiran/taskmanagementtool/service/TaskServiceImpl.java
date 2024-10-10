package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.exception.ResourceNotFoundException;
import com.suryakiran.taskmanagementtool.util.UniqueIdGenerator;
import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.model.Status;
import com.suryakiran.taskmanagementtool.model.Priority;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
// Service implementation for managing Tasks
@Service
public class TaskServiceImpl implements TaskService {
    private static final Logger logger = LoggerFactory.getLogger(TaskServiceImpl.class);

    private final TaskRepository taskRepository;
    private final UniqueIdGenerator uniqueIdGenerator;

    @Autowired
    public TaskServiceImpl(TaskRepository taskRepository, UniqueIdGenerator uniqueIdGenerator) {
        this.taskRepository = taskRepository;
        this.uniqueIdGenerator = uniqueIdGenerator;
    }

    // Create a new task
    @Override
    @Transactional
    public Task createTask(Task task) {
        task.setId(uniqueIdGenerator.generateUniqueId());
        logger.info("Creating task with id: {}", task.getId());
        return taskRepository.save(task);
    }

    //Retrieve all tasks
    @Override
    public List<Task> getAllTasks() {
        logger.info("Fetching all tasks");
        return taskRepository.findAll();
    }

    // Retrieves a task by its ID
    @Override
    public Optional<Task> getTaskById(String id) {
        logger.info("Fetching task with id: {}", id);
        return taskRepository.findById(id);
    }

    // Updates an existing task
    @Override
    @Transactional
    public Task updateTask(String id, Task task) {
        logger.info("Updating task with id: {}", id);
        Optional<Task> existingTask = taskRepository.findById(id);
        if (existingTask.isPresent()) {
            Task updatedTask = existingTask.get();
            updatedTask.setTitle(task.getTitle());
            updatedTask.setDescription(task.getDescription());
            // Set other fields as needed
            return taskRepository.save(updatedTask);
        } else {
            logger.warn("Task not found with id: {}", id);
            throw new ResourceNotFoundException("Task not found with id " + id);
        }
    }

    // Deletes a task by its ID
    @Override
    @Transactional
    public void deleteTask(String id) {
        logger.info("Deleting task with id: {}", id);
        taskRepository.deleteById(id);
    }

    // Retrieves tasks based on status and priority with pagination
    @Override
    public Page<Task> getTasks(Status status, Priority priority, Pageable pageable) {
        if (status != null && priority != null) {
            return taskRepository.findByStatusAndPriority(status, priority, pageable);
        } else if (status != null) {
            return taskRepository.findByStatus(status, pageable);
        } else if (priority != null) {
            return taskRepository.findByPriority(priority, pageable);
        } else {
            return taskRepository.findAll(pageable);
        }
    }
}
package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.exception.ResourceNotFoundException;
import com.suryakiran.taskmanagementtool.util.UniqueIdGenerator;
import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TaskServiceImpl implements TaskService {
    private static final Logger logger = LoggerFactory.getLogger(TaskServiceImpl.class);

    private final TaskRepository taskRepository;
    private final UniqueIdGenerator uniqueIdGenerator;

    public TaskServiceImpl(TaskRepository taskRepository, UniqueIdGenerator uniqueIdGenerator) {
        this.taskRepository = taskRepository;
        this.uniqueIdGenerator = uniqueIdGenerator;
    }

    @Override
    @Transactional
    public Task createTask(Task task) {
        task.setId(uniqueIdGenerator.generateUniqueId());
        logger.info("Creating task with id: {}", task.getId());
        return taskRepository.save(task);
    }

    @Override
    public List<Task> getAllTasks() {
        logger.info("Fetching all tasks");
        return taskRepository.findAll();
    }

    @Override
    public Optional<Task> getTaskById(String id) {
        logger.info("Fetching task with id: {}", id);
        return taskRepository.findById(id);
    }

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

    @Override
    @Transactional
    public void deleteTask(String id) {
        logger.info("Deleting task with id: {}", id);
        taskRepository.deleteById(id);
    }
}
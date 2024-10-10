package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import com.suryakiran.taskmanagementtool.exception.TaskNotFoundException;
import com.suryakiran.taskmanagementtool.model.Priority;
import com.suryakiran.taskmanagementtool.model.Status;
import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import com.suryakiran.taskmanagementtool.util.UniqueIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

    @Override
    public TaskDTO createTask(TaskDTO taskDTO) {
        logger.info("Creating task with title: {}", taskDTO.getTitle());
        Task task = convertToEntity(taskDTO);
        task.setId(uniqueIdGenerator.generateUniqueId());
        Task savedTask = taskRepository.save(task);
        logger.info("Task created with ID: {}", savedTask.getId());
        return convertToDTO(savedTask);
    }

    @Override
    public List<TaskDTO> getAllTasks() {
        logger.info("Retrieving all tasks");
        List<Task> tasks = taskRepository.findAll();
        return Collections.unmodifiableList(tasks.stream().map(this::convertToDTO).toList());
    }

    @Override
    public Optional<TaskDTO> getTaskById(String id) {
        logger.info("Fetching task by ID: {}", id);
        return taskRepository.findById(id).map(this::convertToDTO);
    }

    @Override
    public TaskDTO updateTask(String id, TaskDTO taskDTO) {
        logger.info("Updating task with ID: {}", id);
        Optional<Task> optionalTask = taskRepository.findById(id);
        if (optionalTask.isPresent()) {
            Task task = optionalTask.get();
            task.setTitle(taskDTO.getTitle());
            task.setDescription(taskDTO.getDescription());
            task.setStatus(taskDTO.getStatus());
            task.setPriority(taskDTO.getPriority());
            Task updatedTask = taskRepository.save(task);
            logger.info("Task updated with ID: {}", updatedTask.getId());
            return convertToDTO(updatedTask);
        } else {
            logger.warn("Task not found with ID: {}", id);
            throw new TaskNotFoundException("Task not found with id " + id);
        }
    }

    @Override
    public void deleteTask(String id) {
        logger.info("Deleting task with ID: {}", id);
        taskRepository.deleteById(id);
        logger.info("Task deleted with ID: {}", id);
    }

    @Override
    public Page<TaskDTO> getTasks(Status status, Priority priority, Pageable pageable) {
        logger.info("Filtering tasks with status: {} and priority: {}", status, priority);
        Page<Task> tasks = taskRepository.findByStatusOrPriority(status, priority, pageable);
        return tasks.map(this::convertToDTO);
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
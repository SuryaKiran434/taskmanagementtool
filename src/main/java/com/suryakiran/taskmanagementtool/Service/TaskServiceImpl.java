package com.suryakiran.taskmanagementtool.Service;

import com.suryakiran.taskmanagementtool.Exception.ResourceNotFoundException;
import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.Repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TaskServiceImpl implements TaskService {
    private final TaskRepository taskRepository;

    @Autowired
    public TaskServiceImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    @Transactional
    public Task createTask(Task task) {
        return taskRepository.save(task);
    }

    @Override
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    @Override
    public Optional<Task> getTaskById(Long id) {
        return taskRepository.findById(id);
    }

    @Override
    @Transactional
    public Task updateTask(Long id, Task task) {
        Optional<Task> existingTask = taskRepository.findById(id);
        if (existingTask.isPresent()) {
            Task updatedTask = existingTask.get();
            updatedTask.setName(task.getName());
            updatedTask.setDescription(task.getDescription());
            // Set other fields as needed
            return taskRepository.save(updatedTask);
        } else {
            throw new ResourceNotFoundException("Task not found with id " + id);
        }
    }

    @Override
    @Transactional
    public void deleteTask(Long id) {
        taskRepository.deleteById(id);
    }
}
package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.Controller.TaskController;
import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.service.TaskService;
import com.suryakiran.taskmanagementtool.util.UniqueIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TaskControllerTest {

    @InjectMocks
    private TaskController taskController;

    @Mock
    private TaskService taskService;

    @Mock
    private UniqueIdGenerator uniqueIdGenerator;

    @Test
    public void testCreateTask() {
        when(uniqueIdGenerator.generateUniqueId()).thenReturn("A1B");
        Task task = new Task("A1B", "New Task", "Task Description");
        when(taskService.createTask(any(Task.class))).thenReturn(task);

        ResponseEntity<Task> response = taskController.createTask(task);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(task, response.getBody());
    }

    @Test
    public void testGetAllTasks() {
        List<Task> tasks = new ArrayList<>();
        tasks.add(new Task("A1B", "Task 1", "Description 1"));
        when(taskService.getAllTasks()).thenReturn(tasks);

        List<Task> response = taskController.getAllTasks();
        assertEquals(1, response.size());
        assertEquals("Task 1", response.get(0).getTitle());
    }

    @Test
    public void testGetTaskById() {
        Task task = new Task("A1B", "Task 1", "Description 1");
        when(taskService.getTaskById(anyString())).thenReturn(Optional.of(task));

        ResponseEntity<Task> response = taskController.getTaskById("A1B");
        assertEquals(200, response.getStatusCode().value());
        assertEquals(task, response.getBody());
    }

    @Test
    public void testUpdateTask() {
        Task task = new Task("A1B", "Updated Task", "Updated Description");
        when(taskService.updateTask(anyString(), any(Task.class))).thenReturn(task);

        ResponseEntity<Task> response = taskController.updateTask("A1B", task);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(task, response.getBody());
    }

    @Test
    public void testDeleteTask() {
        doNothing().when(taskService).deleteTask(anyString());

        ResponseEntity<Void> response = taskController.deleteTask("A1B");
        assertEquals(204, response.getStatusCode().value());
    }
}
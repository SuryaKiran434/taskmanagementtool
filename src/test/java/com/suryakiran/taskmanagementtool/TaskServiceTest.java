package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import com.suryakiran.taskmanagementtool.service.TaskServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

public class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskServiceImpl taskService;

    public TaskServiceTest() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGetAllTasks() {
        when(taskRepository.findAll()).thenReturn(Arrays.asList(new Task("A1B", "Task 1", "Description 1")));

        List<Task> tasks = taskService.getAllTasks();
        assertEquals(1, tasks.size());
        assertEquals("Task 1", tasks.get(0).getTitle());
    }

    @Test
    public void testGetTaskById() {
        Task task = new Task("A1B", "Task 1", "Description 1");
        when(taskRepository.findById("A1B")).thenReturn(Optional.of(task));

        Optional<Task> foundTask = taskService.getTaskById("A1B");
        assertTrue(foundTask.isPresent());
        assertEquals("Task 1", foundTask.get().getTitle());
    }

    @Test
    public void testCreateTask() {
        Task task = new Task("A1B", "New Task", "Task Description");
        when(taskRepository.save(task)).thenReturn(task);

        Task createdTask = taskService.createTask(task);
        assertEquals("New Task", createdTask.getTitle());
    }

    @Test
    public void testUpdateTask() {
        Task task = new Task("A1B", "Updated Task", "Updated Description");
        when(taskRepository.findById("A1B")).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        Task updatedTask = taskService.updateTask("A1B", task);
        assertEquals("Updated Task", updatedTask.getTitle());
    }

    @Test
    public void testDeleteTask() {
        Task task = new Task("A1B", "Task to be deleted", "Description");
        when(taskRepository.findById("A1B")).thenReturn(Optional.of(task));
        doNothing().when(taskRepository).deleteById("A1B");

        taskService.deleteTask("A1B");
        verify(taskRepository, times(1)).deleteById("A1B");
    }
}
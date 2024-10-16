package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import com.suryakiran.taskmanagementtool.exception.AuthenticationRequiredException;
import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import com.suryakiran.taskmanagementtool.service.TaskServiceImpl;
import com.suryakiran.taskmanagementtool.util.UniqueIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UniqueIdGenerator uniqueIdGenerator;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private TaskServiceImpl taskService;

    private User user;
    private Task task;
    private TaskDTO taskDTO;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1);
        user.setEmail("test@example.com");

        task = new Task();
        task.setId("1");
        task.setTitle("Test Task");
        task.setUser(user);
        task.setDueDate(java.sql.Date.valueOf(LocalDate.now().plusDays(1))); // Set dueDate

        taskDTO = new TaskDTO();
        taskDTO.setId("1");
        taskDTO.setTitle("Test Task");
        taskDTO.setDueDate(java.sql.Date.valueOf(LocalDate.now().plusDays(1))); // Set dueDate
    }

    @Test
    void testCreateTask() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(uniqueIdGenerator.generateUniqueId()).thenReturn("1");
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        TaskDTO createdTask = taskService.createTask(taskDTO, authentication);

        assertNotNull(createdTask);
        assertEquals("1", createdTask.getId());
        assertEquals(taskDTO.getDueDate(), createdTask.getDueDate()); // Verify dueDate
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void testCreateTask_AuthenticationRequired() {
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThrows(AuthenticationRequiredException.class, () -> taskService.createTask(taskDTO, authentication));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void testDeleteTask() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(taskRepository.findByIdAndUser("1", user)).thenReturn(Optional.of(task));

        taskService.deleteTask("1", authentication);

        verify(taskRepository, times(1)).delete(task);
    }

    @Test
    void testDeleteTask_AuthenticationRequired() {
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThrows(AuthenticationRequiredException.class, () -> taskService.deleteTask("1", authentication));
        verify(taskRepository, never()).delete(any());
    }

    @Test
    void testUpdateTask() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(taskRepository.findByIdAndUser("1", user)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        TaskDTO updatedTask = taskService.updateTask("1", taskDTO, authentication);

        assertNotNull(updatedTask);
        assertEquals("1", updatedTask.getId());
        assertEquals(taskDTO.getDueDate(), updatedTask.getDueDate()); // Verify dueDate
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void testUpdateTask_AuthenticationRequired() {
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThrows(AuthenticationRequiredException.class, () -> taskService.updateTask("1", taskDTO, authentication));
        verify(taskRepository, never()).save(any());
    }
}
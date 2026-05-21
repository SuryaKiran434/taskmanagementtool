package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import com.suryakiran.taskmanagementtool.exception.AuthenticationRequiredException;
import com.suryakiran.taskmanagementtool.model.*;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import com.suryakiran.taskmanagementtool.service.ActivityLogService;
import com.suryakiran.taskmanagementtool.service.TaskConversionService;
import com.suryakiran.taskmanagementtool.service.TaskServiceImpl;
import com.suryakiran.taskmanagementtool.util.UniqueIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @Mock private UniqueIdGenerator uniqueIdGenerator;
    @Mock private ActivityLogService activityLogService;
    @Mock private Authentication authentication;

    private TaskConversionService taskConversionService;
    private TaskServiceImpl taskService;

    private User user;
    private Task task;
    private TaskDTO taskDTO;

    @BeforeEach
    void setUp() {
        taskConversionService = new TaskConversionService();
        taskService = new TaskServiceImpl(taskRepository, userRepository, uniqueIdGenerator,
                taskConversionService, activityLogService);

        user = new User();
        user.setId(1);
        user.setEmail("test@example.com");

        task = new Task();
        task.setId("task-1");
        task.setTitle("Test Task");
        task.setDescription("Test Description");
        task.setStatus(Status.TO_DO);
        task.setPriority(Priority.MEDIUM);
        task.setUser(user);
        task.setDueDate(LocalDate.now().plusDays(1));

        taskDTO = new TaskDTO();
        taskDTO.setId("task-1");
        taskDTO.setTitle("Test Task");
        taskDTO.setDescription("Test Description");
        taskDTO.setStatus(Status.TO_DO);
        taskDTO.setPriority(Priority.MEDIUM);
        taskDTO.setDueDate(LocalDate.now().plusDays(1));
    }

    @Test
    void testCreateTask_Success() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(uniqueIdGenerator.generateUniqueId()).thenReturn("task-1");
        when(taskRepository.save(any(Task.class))).thenReturn(task);
        doNothing().when(activityLogService).log(anyString(), any(), any(), any(), any(), any());

        TaskDTO createdTask = taskService.createTask(taskDTO, authentication);

        assertNotNull(createdTask);
        assertEquals("task-1", createdTask.getId());
        assertEquals("Test Task", createdTask.getTitle());
        verify(taskRepository, times(1)).save(any(Task.class));
        verify(activityLogService, times(1)).log(anyString(), any(), eq(ActivityAction.TASK_CREATED), any(), any(), any());
    }

    @Test
    void testCreateTask_AuthenticationRequired() {
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThrows(AuthenticationRequiredException.class, () -> taskService.createTask(taskDTO, authentication));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void testDeleteTask_SoftDelete() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(taskRepository.findByIdAndUser("task-1", user)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenReturn(task);
        doNothing().when(activityLogService).log(anyString(), any(), any(), any(), any(), any());

        taskService.deleteTask("task-1", authentication);

        // Soft delete: save is called (not delete), and deletedAt is set
        verify(taskRepository, times(1)).save(any(Task.class));
        assertNotNull(task.getDeletedAt());
        verify(taskRepository, never()).delete(any());
    }

    @Test
    void testDeleteTask_AuthenticationRequired() {
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThrows(AuthenticationRequiredException.class, () -> taskService.deleteTask("task-1", authentication));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void testUpdateTask_Success() {
        TaskDTO updateDTO = new TaskDTO();
        updateDTO.setId("task-1");
        updateDTO.setTitle("Updated Task");
        updateDTO.setDescription("Updated Description");
        updateDTO.setStatus(Status.IN_PROGRESS);
        updateDTO.setPriority(Priority.HIGH);
        updateDTO.setDueDate(LocalDate.now().plusDays(2));

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(taskRepository.findByIdAndUser("task-1", user)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(activityLogService).log(anyString(), any(), any(), any(), any(), any());

        TaskDTO updatedTask = taskService.updateTask("task-1", updateDTO, authentication);

        assertNotNull(updatedTask);
        assertEquals("Updated Task", updatedTask.getTitle());
        assertEquals(Status.IN_PROGRESS, updatedTask.getStatus());
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void testUpdateTask_AuthenticationRequired() {
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThrows(AuthenticationRequiredException.class, () -> taskService.updateTask("task-1", taskDTO, authentication));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void testRestoreTask_Success() {
        task.setDeletedAt(java.time.Instant.now().minusSeconds(3600));
        when(authentication.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(taskRepository.findByIdAndUser("task-1", user)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(activityLogService).log(anyString(), any(), any(), any(), any(), any());

        TaskDTO restored = taskService.restoreTask("task-1", authentication);

        assertNotNull(restored);
        assertNull(task.getDeletedAt());
        verify(activityLogService, times(1)).log(anyString(), any(), eq(ActivityAction.TASK_RESTORED), any(), any(), any());
    }

    @Test
    void testBulkDelete_Success() {
        when(authentication.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(taskRepository.findByIdAndUser("task-1", user)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        int deleted = taskService.bulkDeleteTasks(java.util.List.of("task-1"), authentication);

        assertEquals(1, deleted);
        assertNotNull(task.getDeletedAt());
    }
}

package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.controller.TaskController;
import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import com.suryakiran.taskmanagementtool.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskControllerTest {

    @Mock
    private TaskService taskService;

    @InjectMocks
    private TaskController taskController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateTask() {
        TaskDTO taskDTO = new TaskDTO();
        taskDTO.setTitle("New Task");

        when(taskService.createTask(any(TaskDTO.class))).thenReturn(taskDTO);

        ResponseEntity<TaskDTO> response = taskController.createTask(taskDTO);

        assertNotNull(response.getBody());
        assertEquals("New Task", response.getBody().getTitle());

        verify(taskService, times(1)).createTask(any(TaskDTO.class));
    }
}
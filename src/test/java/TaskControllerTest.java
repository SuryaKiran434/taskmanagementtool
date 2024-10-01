

import com.suryakiran.taskmanagementtool.Controller.TaskController;
import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.Service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

public class TaskControllerTest {

    @InjectMocks
    private TaskController taskController;

    @Mock
    private TaskService taskService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testCreateTask() {
        Task task = new Task("New Task", "Task Description");
        when(taskService.createTask(any(Task.class))).thenReturn(task);

        ResponseEntity<Task> response = taskController.createTask(task);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(task, response.getBody());
    }

    @Test
    public void testGetAllTasks() {
        List<Task> tasks = new ArrayList<>();
        tasks.add(new Task("Task 1", "Description 1"));
        when(taskService.getAllTasks()).thenReturn(tasks);

        List<Task> response = taskController.getAllTasks();
        assertEquals(1, response.size());
        assertEquals("Task 1", response.get(0).getName());
    }

    @Test
    public void testGetTaskById() {
        Task task = new Task("Task 1", "Description 1");
        when(taskService.getTaskById(anyLong())).thenReturn(Optional.of(task));

        ResponseEntity<Task> response = taskController.getTaskById(1L);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(task, response.getBody());
    }

    @Test
    public void testUpdateTask() {
        Task task = new Task("Updated Task", "Updated Description");
        when(taskService.updateTask(anyLong(), any(Task.class))).thenReturn(task);

        ResponseEntity<Task> response = taskController.updateTask(1L, task);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(task, response.getBody());
    }

    @Test
    public void testDeleteTask() {
        doNothing().when(taskService).deleteTask(anyLong());

        ResponseEntity<Void> response = taskController.deleteTask(1L);
        assertEquals(204, response.getStatusCodeValue());
    }
}

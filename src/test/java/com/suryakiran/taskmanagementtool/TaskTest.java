package com.suryakiran.taskmanagementtool;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import com.suryakiran.taskmanagementtool.model.Task;
public class TaskTest {

    @Test
    public void testTaskCreation() {
        Task task = new Task("A1B", "Test Task", "This is a test task.");

        assertNotNull(task);
        assertEquals("Test Task", task.getTitle());
        assertEquals("This is a test task.", task.getDescription());
    }

    @Test
    public void testSetters() {
        Task task = new Task();
        task.setId("1");
        task.setTitle("Updated Task");
        task.setDescription("This is an updated task.");

        assertEquals("1", task.getId());
        assertEquals("Updated Task", task.getTitle());
        assertEquals("This is an updated task.", task.getDescription());
    }
}
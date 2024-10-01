import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.suryakiran.taskmanagementtool.model.Task;
import org.junit.jupiter.api.Test;

public class TaskTest {

    @Test
    public void testTaskCreation() {
        Task task = new Task("Test Task", "This is a test task.");

        assertNotNull(task);
        assertEquals("Test Task", task.getName());
        assertEquals("This is a test task.", task.getDescription());
    }

    @Test
    public void testSetters() {
        Task task = new Task();
        task.setId(1L);
        task.setName("Updated Task");
        task.setDescription("This is an updated task.");

        assertEquals(1L, task.getId());
        assertEquals("Updated Task", task.getName());
        assertEquals("This is an updated task.", task.getDescription());
    }
}
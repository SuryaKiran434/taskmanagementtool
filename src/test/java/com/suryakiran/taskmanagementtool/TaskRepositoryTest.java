package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import com.suryakiran.taskmanagementtool.util.UniqueIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.ANY)
@ComponentScan(basePackages = "com.suryakiran.taskmanagementtool")
public class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UniqueIdGenerator uniqueIdGenerator;

    @Test
    public void testSaveAndFindById() {
        Task task = new Task();
        task.setId(uniqueIdGenerator.generateUniqueId());
        task.setTitle("Test Task");
        task.setDescription("This is a test task");

        taskRepository.save(task);

        assertTrue(taskRepository.existsById(task.getId()));
    }
}
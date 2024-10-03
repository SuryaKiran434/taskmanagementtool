package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
public class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Test
    public void testSaveAndFindById() {
        Task task = new Task("A1B", "Test Task", "This is a test task.");
        taskRepository.save(task);

        Optional<Task> foundTask = taskRepository.findById("A1B");
        assertTrue(foundTask.isPresent());
    }
}
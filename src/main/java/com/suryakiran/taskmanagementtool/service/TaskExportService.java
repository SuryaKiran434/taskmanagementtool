package com.suryakiran.taskmanagementtool.service;

import com.opencsv.CSVWriter;
import com.suryakiran.taskmanagementtool.exception.UserNotFoundException;
import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class TaskExportService {

    private static final Logger logger = LoggerFactory.getLogger(TaskExportService.class);

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskExportService(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    public byte[] exportTasksAsCsv(Authentication authentication) throws IOException {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        List<Task> tasks = taskRepository.findByUser(user, PageRequest.of(0, 10000)).getContent();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            writer.writeNext(new String[]{"ID", "Title", "Description", "Status", "Priority",
                    "Due Date", "Category", "Created At", "Project"});
            for (Task task : tasks) {
                writer.writeNext(new String[]{
                        task.getId(),
                        task.getTitle(),
                        task.getDescription() != null ? task.getDescription() : "",
                        task.getStatus() != null ? task.getStatus().name() : "",
                        task.getPriority() != null ? task.getPriority().name() : "",
                        task.getDueDate() != null ? task.getDueDate().toString() : "",
                        task.getCategory() != null ? task.getCategory() : "",
                        task.getCreatedAt() != null ? task.getCreatedAt().toString() : "",
                        task.getProject() != null ? task.getProject().getName() : ""
                });
            }
        }
        logger.info("Exported {} tasks to CSV for user id: {}", tasks.size(), user.getId());
        return out.toByteArray();
    }
}

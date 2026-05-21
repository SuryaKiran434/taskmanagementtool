package com.suryakiran.taskmanagementtool.util;

import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates unique 8-character alphanumeric task IDs.
 * 36^8 = ~2.8 trillion possible values — collision probability is negligible.
 * Uniqueness is guaranteed by checking the database on each generation.
 */
@Component
public class UniqueIdGenerator {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int ID_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TaskRepository taskRepository;

    public UniqueIdGenerator(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public String generateUniqueId() {
        String id;
        do {
            id = generateRandomId();
        } while (taskRepository.existsById(id));
        return id;
    }

    private String generateRandomId() {
        StringBuilder id = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            id.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return id.toString();
    }
}

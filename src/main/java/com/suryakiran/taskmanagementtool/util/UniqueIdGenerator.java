package com.suryakiran.taskmanagementtool.util;

import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

@Component
public class UniqueIdGenerator {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int ID_LENGTH = 3;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Set<String> generatedIds = new HashSet<>();

    @Autowired
    private TaskRepository taskRepository;

    public synchronized String generateUniqueId() {
        String id;
        do {
            id = generateRandomId();
        } while (generatedIds.contains(id) || taskRepository.existsById(id));
        generatedIds.add(id);
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
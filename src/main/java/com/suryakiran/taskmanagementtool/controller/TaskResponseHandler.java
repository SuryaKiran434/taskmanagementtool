package com.suryakiran.taskmanagementtool.controller;

import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Handler for task response processing.
 * Implements Strategy pattern for response handling.
 */
@Component
public class TaskResponseHandler {

    /**
     * Handles task response based on whether the task exists
     * @param taskDTO optional task DTO
     * @return appropriate HTTP response
     */
    public ResponseEntity<TaskDTO> handleTaskResponse(Optional<TaskDTO> taskDTO) {
        return taskDTO.map(ResponseEntity::ok)
                     .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Creates a success response with the task
     * @param taskDTO the task DTO
     * @return success response
     */
    public ResponseEntity<TaskDTO> createSuccessResponse(TaskDTO taskDTO) {
        return ResponseEntity.ok(taskDTO);
    }

    /**
     * Creates a not found response
     * @return not found response
     */
    public ResponseEntity<TaskDTO> createNotFoundResponse() {
        return ResponseEntity.notFound().build();
    }

    /**
     * Creates an error response
     * @param statusCode the HTTP status code
     * @return error response
     */
    public ResponseEntity<TaskDTO> createErrorResponse(int statusCode) {
        return ResponseEntity.status(statusCode).build();
    }
}

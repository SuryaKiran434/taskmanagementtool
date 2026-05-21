package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.LabelDTO;
import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import com.suryakiran.taskmanagementtool.dto.UserDTO;
import com.suryakiran.taskmanagementtool.model.Label;
import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.model.User;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class TaskConversionService {

    public TaskDTO convertToDTO(Task task) {
        if (task == null) {
            return null;
        }

        TaskDTO taskDTO = new TaskDTO();
        taskDTO.setId(task.getId());
        taskDTO.setTitle(task.getTitle());
        taskDTO.setDescription(task.getDescription());
        taskDTO.setStatus(task.getStatus());
        taskDTO.setPriority(task.getPriority());
        taskDTO.setDueDate(task.getDueDate());
        taskDTO.setCategory(task.getCategory());
        taskDTO.setCreatedAt(task.getCreatedAt());
        taskDTO.setUpdatedAt(task.getUpdatedAt());

        if (task.getProject() != null) {
            taskDTO.setProjectId(task.getProject().getId());
            taskDTO.setProjectName(task.getProject().getName());
        }

        // Subtask counts (safe: uses size() which works even if lazy-loaded in same session)
        if (Hibernate.isInitialized(task.getSubtasks())) {
            taskDTO.setSubtaskCount(task.getSubtasks().size());
            taskDTO.setCompletedSubtaskCount(
                    (int) task.getSubtasks().stream().filter(s -> s.isCompleted()).count());
        }

        // Labels
        if (Hibernate.isInitialized(task.getLabels())) {
            Set<Label> labels = task.getLabels();
            if (labels != null && !labels.isEmpty()) {
                taskDTO.setLabels(labels.stream().map(this::convertLabelToDTO).toList());
            }
        }

        User creator = task.getUser();
        if (creator != null) {
            taskDTO.setCreatorFirstName(creator.getFirstName());
            taskDTO.setCreatorLastName(creator.getLastName());
            taskDTO.setCreator(convertUserToDTO(creator));
        }

        User assignee = task.getAssignee();
        if (assignee != null) {
            taskDTO.setAssigneeId(assignee.getId());
            taskDTO.setAssigneeFirstName(assignee.getFirstName());
            taskDTO.setAssigneeLastName(assignee.getLastName());
            taskDTO.setAssigneeEmail(assignee.getEmail());
            taskDTO.setAssigneeAvatarUrl(assignee.getAvatarUrl());
        }

        return taskDTO;
    }

    public Task convertToEntity(TaskDTO taskDTO) {
        if (taskDTO == null) {
            return null;
        }

        Task task = new Task();
        task.setId(taskDTO.getId());
        task.setTitle(taskDTO.getTitle());
        task.setDescription(taskDTO.getDescription());
        task.setStatus(taskDTO.getStatus());
        task.setPriority(taskDTO.getPriority());
        task.setDueDate(taskDTO.getDueDate());
        task.setCategory(taskDTO.getCategory());

        return task;
    }

    private UserDTO convertUserToDTO(User user) {
        if (user == null) {
            return null;
        }

        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setFirstName(user.getFirstName());
        userDTO.setLastName(user.getLastName());
        userDTO.setEmail(user.getEmail());
        userDTO.setCreatedAt(user.getCreatedAt());
        userDTO.setAvatarUrl(user.getAvatarUrl());

        return userDTO;
    }

    private LabelDTO convertLabelToDTO(Label label) {
        LabelDTO dto = new LabelDTO();
        dto.setId(label.getId());
        dto.setName(label.getName());
        dto.setColor(label.getColor());
        return dto;
    }

    public List<TaskDTO> convertToDTOList(List<Task> tasks) {
        if (tasks == null) return List.of();
        return tasks.stream().map(this::convertToDTO).toList();
    }
}

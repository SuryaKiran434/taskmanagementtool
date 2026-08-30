package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import com.suryakiran.taskmanagementtool.dto.TaskStatsDTO;
import com.suryakiran.taskmanagementtool.exception.AuthenticationRequiredException;
import com.suryakiran.taskmanagementtool.exception.TaskNotFoundException;
import com.suryakiran.taskmanagementtool.exception.UserNotFoundException;
import com.suryakiran.taskmanagementtool.model.*;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import com.suryakiran.taskmanagementtool.util.UniqueIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.suryakiran.taskmanagementtool.util.LogSanitizer.sanitize;

@Service
public class TaskServiceImpl implements TaskService {

    private static final Logger logger = LoggerFactory.getLogger(TaskServiceImpl.class);
    private static final String USER_NOT_FOUND = "User not found";
    private static final String AUTHENTICATION_REQUIRED = "Authentication required";

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final UniqueIdGenerator uniqueIdGenerator;
    private final TaskConversionService taskConversionService;
    private final ActivityLogService activityLogService;

    @Autowired
    public TaskServiceImpl(TaskRepository taskRepository, UserRepository userRepository,
                           UniqueIdGenerator uniqueIdGenerator, TaskConversionService taskConversionService,
                           ActivityLogService activityLogService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.uniqueIdGenerator = uniqueIdGenerator;
        this.taskConversionService = taskConversionService;
        this.activityLogService = activityLogService;
    }

    @Override
    @Transactional
    public TaskDTO createTask(TaskDTO taskDTO, Authentication authentication) {
        logger.info("Creating task with title: {}", sanitize(taskDTO.getTitle()));
        if (!authentication.isAuthenticated()) {
            throw new AuthenticationRequiredException(AUTHENTICATION_REQUIRED);
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        Task task = taskConversionService.convertToEntity(taskDTO);
        task.setId(uniqueIdGenerator.generateUniqueId());
        task.setUser(user);
        if (taskDTO.getAssigneeId() != null) {
            User assignee = userRepository.findById(Long.valueOf(taskDTO.getAssigneeId()))
                    .orElseThrow(() -> new UserNotFoundException("Assignee not found"));
            task.setAssignee(assignee);
        }
        Task savedTask = taskRepository.save(task);
        activityLogService.log(savedTask.getId(), user, ActivityAction.TASK_CREATED,
                null, savedTask.getTitle(), "Task created");
        logger.info("Task created with ID: {}", savedTask.getId());
        return taskConversionService.convertToDTO(savedTask);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskDTO> getAllTasks(Pageable pageable) {
        return taskRepository.findAll(pageable).map(taskConversionService::convertToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskDTO> getAllTasks(Pageable pageable, Authentication authentication) {
        logger.info("Retrieving all tasks for user: {}", sanitize(authentication.getName()));
        if (!authentication.isAuthenticated()) {
            return getAllTasks(pageable);
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        return taskRepository.findByUser(user, pageable).map(taskConversionService::convertToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskDTO> getTaskById(String id) {
        return taskRepository.findById(id).map(taskConversionService::convertToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskDTO> getTaskById(String id, Authentication authentication) {
        if (!authentication.isAuthenticated()) {
            return getTaskById(id);
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        return taskRepository.findByIdAndUser(id, user).map(taskConversionService::convertToDTO);
    }

    @Override
    @Transactional
    public TaskDTO updateTask(String id, TaskDTO taskDTO, Authentication authentication) {
        logger.info("Updating task with ID: {}", sanitize(id));
        if (!authentication.isAuthenticated()) {
            throw new AuthenticationRequiredException(AUTHENTICATION_REQUIRED);
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        Task task = taskRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new TaskNotFoundException("Task not found"));

        // Track meaningful changes for the activity log
        Status oldStatus = task.getStatus();
        Priority oldPriority = task.getPriority();

        Integer oldAssigneeId = task.getAssignee() != null ? task.getAssignee().getId() : null;

        task.setTitle(taskDTO.getTitle());
        task.setDescription(taskDTO.getDescription());
        task.setStatus(taskDTO.getStatus());
        task.setPriority(taskDTO.getPriority());
        task.setDueDate(taskDTO.getDueDate());
        task.setCategory(taskDTO.getCategory());
        if (taskDTO.getAssigneeId() != null) {
            User assignee = userRepository.findById(Long.valueOf(taskDTO.getAssigneeId()))
                    .orElseThrow(() -> new UserNotFoundException("Assignee not found"));
            task.setAssignee(assignee);
        } else {
            task.setAssignee(null);
        }
        Task updatedTask = taskRepository.save(task);

        Integer newAssigneeId = updatedTask.getAssignee() != null ? updatedTask.getAssignee().getId() : null;
        if (!java.util.Objects.equals(oldAssigneeId, newAssigneeId)) {
            activityLogService.log(id, user, ActivityAction.TASK_UPDATED,
                    String.valueOf(oldAssigneeId), String.valueOf(newAssigneeId), "Assignee changed");
        }

        if (oldStatus != taskDTO.getStatus()) {
            activityLogService.log(id, user, ActivityAction.STATUS_CHANGED,
                    oldStatus.name(), taskDTO.getStatus().name(), "Status changed");
        }
        if (oldPriority != taskDTO.getPriority()) {
            activityLogService.log(id, user, ActivityAction.PRIORITY_CHANGED,
                    oldPriority.name(), taskDTO.getPriority().name(), "Priority changed");
        }
        activityLogService.log(id, user, ActivityAction.TASK_UPDATED,
                null, null, "Task updated");

        logger.info("Task updated with ID: {}", updatedTask.getId());
        return taskConversionService.convertToDTO(updatedTask);
    }

    @Override
    @Transactional
    public void deleteTask(String id, Authentication authentication) {
        logger.info("Soft-deleting task with ID: {}", sanitize(id));
        if (!authentication.isAuthenticated()) {
            throw new AuthenticationRequiredException(AUTHENTICATION_REQUIRED);
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        Task task = taskRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new TaskNotFoundException("Task not found"));
        task.setDeletedAt(Instant.now());
        taskRepository.save(task);
        activityLogService.log(id, user, ActivityAction.TASK_DELETED, null, null, "Task soft-deleted");
        logger.info("Task soft-deleted with ID: {}", sanitize(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskDTO> getTasks(Status status, Priority priority, Pageable pageable) {
        return taskRepository.findByStatusAndPriority(status, priority, pageable)
                .map(taskConversionService::convertToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskDTO> getTasks(Status status, Priority priority, Pageable pageable, Authentication authentication) {
        if (!authentication.isAuthenticated()) {
            return getTasks(status, priority, pageable);
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        return taskRepository.findByUserAndStatusAndPriority(user, status, priority, pageable)
                .map(taskConversionService::convertToDTO);
    }

    @Override
    public boolean isTaskOwner(String taskId, int userId) {
        return taskRepository.existsByIdAndUserId(taskId, userId);
    }

    @Override
    @Transactional
    public TaskDTO restoreTask(String id, Authentication authentication) {
        logger.info("Restoring task with ID: {}", sanitize(id));
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        Task task = taskRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new TaskNotFoundException("Task not found"));
        task.setDeletedAt(null);
        Task restoredTask = taskRepository.save(task);
        activityLogService.log(id, user, ActivityAction.TASK_RESTORED, null, null, "Task restored");
        logger.info("Task restored with ID: {}", sanitize(id));
        return taskConversionService.convertToDTO(restoredTask);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskDTO> searchTasks(String query, Pageable pageable, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        return taskRepository.searchByUser(user, query, pageable)
                .map(taskConversionService::convertToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskStatsDTO getTaskStats(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        LocalDate today = LocalDate.now();
        // One conditional-aggregation pass over the user's rows, instead of five COUNT queries
        // scanning the same rows five times.
        TaskRepository.TaskStatsProjection stats = taskRepository.getStatsByUser(
                user, Status.TO_DO, Status.IN_PROGRESS, Status.COMPLETE, today);
        return new TaskStatsDTO(
                orZero(stats.getTotal()),
                orZero(stats.getToDo()),
                orZero(stats.getInProgress()),
                orZero(stats.getComplete()),
                orZero(stats.getOverdue()));
    }

    /** SUM(...) is NULL when the user has no tasks at all; the DTO contract is zeroes. */
    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    @Override
    @Transactional
    public int bulkUpdateTasks(List<String> taskIds, Status status, Priority priority, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        if (taskIds == null || taskIds.isEmpty()) {
            return 0;
        }
        // Load the whole batch in one round trip. The finder applies the same ownership and
        // soft-delete predicates as findByIdAndUser, so a task belonging to someone else is
        // never returned and therefore never modified or counted.
        List<Task> tasks = taskRepository.findAllByIdInAndUser(taskIds, user);
        for (Task task : tasks) {
            if (status != null) task.setStatus(status);
            if (priority != null) task.setPriority(priority);
        }
        taskRepository.saveAll(tasks);
        return tasks.size();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskDTO> getTasksForUser(int userId, Pageable pageable) {
        User user = userRepository.findById((long) userId)
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        return taskRepository.findByUser(user, pageable).map(taskConversionService::convertToDTO);
    }

    @Override
    @Transactional
    public int bulkDeleteTasks(List<String> taskIds, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        if (taskIds == null || taskIds.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        // Same ownership/soft-delete predicates as findByIdAndUser, applied to the batch at once.
        List<Task> tasks = taskRepository.findAllByIdInAndUser(taskIds, user);
        for (Task task : tasks) {
            task.setDeletedAt(now);
        }
        taskRepository.saveAll(tasks);
        return tasks.size();
    }
}

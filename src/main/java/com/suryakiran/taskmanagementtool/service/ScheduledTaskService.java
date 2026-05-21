package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.model.NotificationType;
import com.suryakiran.taskmanagementtool.model.Status;
import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ScheduledTaskService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledTaskService.class);

    private final TaskRepository taskRepository;
    private final NotificationServiceImpl notificationService;

    public ScheduledTaskService(TaskRepository taskRepository,
                                 NotificationServiceImpl notificationService) {
        this.taskRepository = taskRepository;
        this.notificationService = notificationService;
    }

    // Every day at 8:00 AM
    @Scheduled(cron = "0 0 8 * * *")
    public void sendDueSoonNotifications() {
        logger.info("Running due-soon notification job");
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // Find tasks due tomorrow that are not complete and not soft-deleted
        List<Task> tasks = taskRepository.findTasksDueSoon(tomorrow, PageRequest.of(0, 1000)).getContent();
        for (Task task : tasks) {
            if (task.getUser() != null) {
                notificationService.createNotification(
                        task.getUser(),
                        NotificationType.TASK_DUE_SOON,
                        "Task \"" + task.getTitle() + "\" is due tomorrow",
                        task.getId()
                );
            }
        }
        logger.info("Sent {} due-soon notifications", tasks.size());
    }

    // Every day at 9:00 AM
    @Scheduled(cron = "0 0 9 * * *")
    public void sendOverdueNotifications() {
        logger.info("Running overdue notification job");
        LocalDate today = LocalDate.now();

        List<Task> overdueTasks = taskRepository.findOverdueTasks(today, PageRequest.of(0, 1000)).getContent();
        for (Task task : overdueTasks) {
            if (task.getUser() != null) {
                notificationService.createNotification(
                        task.getUser(),
                        NotificationType.TASK_OVERDUE,
                        "Task \"" + task.getTitle() + "\" is overdue",
                        task.getId()
                );
            }
        }
        logger.info("Sent {} overdue notifications", overdueTasks.size());
    }
}

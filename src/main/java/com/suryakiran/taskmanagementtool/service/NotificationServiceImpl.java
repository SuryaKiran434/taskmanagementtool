package com.suryakiran.taskmanagementtool.service;

import com.suryakiran.taskmanagementtool.dto.NotificationDTO;
import com.suryakiran.taskmanagementtool.exception.UserNotFoundException;
import com.suryakiran.taskmanagementtool.model.Notification;
import com.suryakiran.taskmanagementtool.model.NotificationType;
import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.repository.NotificationRepository;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                    UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> getUnreadNotifications(Authentication authentication) {
        User user = getUser(authentication.getName());
        return notificationRepository.findByUserAndReadFalseOrderByCreatedAtDesc(user)
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> getAllNotifications(Authentication authentication) {
        User user = getUser(authentication.getName());
        return notificationRepository.findByUserOrderByCreatedAtDesc(user, PageRequest.of(0, 50))
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Authentication authentication) {
        User user = getUser(authentication.getName());
        return notificationRepository.countByUserAndReadFalse(user);
    }

    @Transactional
    public void markAsRead(Long notificationId, Authentication authentication) {
        User user = getUser(authentication.getName());
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUser().getId() == user.getId()) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    @Transactional
    public void markAllAsRead(Authentication authentication) {
        User user = getUser(authentication.getName());
        notificationRepository.markAllReadForUser(user);
    }

    public void createNotification(User user, NotificationType type, String message, String taskId) {
        try {
            Notification notification = new Notification();
            notification.setUser(user);
            notification.setType(type);
            notification.setMessage(message);
            notification.setTaskId(taskId);
            notificationRepository.save(notification);
        } catch (Exception e) {
            logger.error("Failed to create notification for user {}: {}", user.getId(), e.getMessage());
        }
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private NotificationDTO toDTO(Notification n) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(n.getId());
        dto.setType(n.getType());
        dto.setMessage(n.getMessage());
        dto.setRead(n.isRead());
        dto.setTaskId(n.getTaskId());
        dto.setCreatedAt(n.getCreatedAt());
        return dto;
    }
}

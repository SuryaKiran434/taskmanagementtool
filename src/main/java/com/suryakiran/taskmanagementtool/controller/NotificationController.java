package com.suryakiran.taskmanagementtool.controller;

import com.suryakiran.taskmanagementtool.dto.NotificationDTO;
import com.suryakiran.taskmanagementtool.service.NotificationServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.suryakiran.taskmanagementtool.util.LogSanitizer.maskEmail;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);
    private final NotificationServiceImpl notificationService;

    public NotificationController(NotificationServiceImpl notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationDTO>> getAllNotifications(Authentication authentication) {
        logger.debug("GET all notifications for: {}", maskEmail(authentication.getName()));
        return ResponseEntity.ok(notificationService.getAllNotifications(authentication));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationDTO>> getUnread(Authentication authentication) {
        logger.debug("GET unread notifications for: {}", maskEmail(authentication.getName()));
        return ResponseEntity.ok(notificationService.getUnreadNotifications(authentication));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(authentication)));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id, Authentication authentication) {
        logger.info("Mark notification {} as read for: {}", id, maskEmail(authentication.getName()));
        notificationService.markAsRead(id, authentication);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        logger.info("Mark all notifications read for: {}", maskEmail(authentication.getName()));
        notificationService.markAllAsRead(authentication);
        return ResponseEntity.noContent().build();
    }
}

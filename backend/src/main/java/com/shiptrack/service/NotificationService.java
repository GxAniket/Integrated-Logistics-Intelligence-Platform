package com.shiptrack.service;

import com.shiptrack.dto.NotificationResponse;
import com.shiptrack.entity.Notification;
import com.shiptrack.entity.User;
import com.shiptrack.exception.ResourceNotFoundException;
import com.shiptrack.repository.NotificationRepository;
import com.shiptrack.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getCurrentUserNotifications() {
        Long userId = getCurrentUser().getId();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void markCurrentUserNotificationsRead() {
        Long userId = getCurrentUser().getId();
        List<Notification> unread = notificationRepository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        unread.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(unread);
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in user not found."));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getShipment() == null ? null : notification.getShipment().getId(),
                notification.getShipment() == null ? null : notification.getShipment().getTrackingNumber());
    }
}

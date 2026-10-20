package com.shiptrack.dto;

import com.shiptrack.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        boolean read,
        LocalDateTime createdAt,
        Long shipmentId,
        String trackingNumber) {
}

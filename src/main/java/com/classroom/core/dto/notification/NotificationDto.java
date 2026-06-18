package com.classroom.core.dto.notification;

import com.classroom.core.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private UUID id;
    private NotificationType type;
    private String title;
    private String message;
    private Boolean read;
    private Instant createdAt;
}

package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.notification.NotificationDto;
import com.classroom.core.model.Notification;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "User notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(
            summary = "Get current user's notifications",
            operationId = "getMyNotifications",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of notifications")
            }
    )
    public ResponseEntity<List<NotificationDto>> getMyNotifications(
            @AuthenticationPrincipal UserPrincipal principal) {

        List<Notification> notifications = notificationService.getMyNotifications(principal.getId());
        return ResponseEntity.ok(notifications.stream()
                .map(this::toDto)
                .toList());
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "Count unread notifications",
            operationId = "countUnreadNotifications",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Unread count")
            }
    )
    public ResponseEntity<Long> countUnread(
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(notificationService.countUnread(principal.getId()));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(
            summary = "Mark a notification as read",
            operationId = "markNotificationAsRead",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Marked as read"),
                    @ApiResponse(responseCode = "403", description = "Not your notification",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Notification not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID notificationId) {

        notificationService.markAsRead(notificationId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    private NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .read(notification.getRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}

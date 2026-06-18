package com.classroom.core.service;

import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CourseMemberRepository courseMemberRepository;

    @Transactional
    public Notification notifyUser(User user, NotificationType type, String title, String message) {
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .read(false)
                .build();
        return notificationRepository.save(notification);
    }

    @Transactional
    public void notifyTeachers(UUID courseId, String title, String message) {
        List<CourseMember> teachers = courseMemberRepository
                .findByCourseIdAndRoleOrderByJoinedAtAsc(courseId, CourseRole.TEACHER);
        for (CourseMember teacher : teachers) {
            notifyUser(teacher.getUser(), NotificationType.PEER_REVIEW, title, message);
        }
    }

    public List<Notification> getMyNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!notification.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You can only mark your own notifications as read");
        }
        notification.setRead(true);
    }
}

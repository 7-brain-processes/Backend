package com.classroom.core.service;

import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private CourseMemberRepository courseMemberRepository;

    @InjectMocks
    private NotificationService notificationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    private User buildUser(UUID id) {
        return User.builder()
                .id(id)
                .username("user-" + id.toString().substring(0, 4))
                .passwordHash("h")
                .createdAt(Instant.now())
                .build();
    }

    private Notification buildNotification(UUID ownerId) {
        return Notification.builder()
                .id(notificationId)
                .user(buildUser(ownerId))
                .type(NotificationType.PEER_REVIEW)
                .title("Title")
                .message("Message")
                .read(false)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void notifyUser_savesNotification() {
        User user = buildUser(userId);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification result = notificationService.notifyUser(user, NotificationType.SYSTEM, "T", "M");

        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getType()).isEqualTo(NotificationType.SYSTEM);
        assertThat(result.getTitle()).isEqualTo("T");
        assertThat(result.getMessage()).isEqualTo("M");
        assertThat(result.getRead()).isFalse();
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notifyTeachers_notifiesAllTeachers() {
        User teacher1 = buildUser(UUID.randomUUID());
        User teacher2 = buildUser(UUID.randomUUID());
        Course course = Course.builder().id(courseId).name("C").build();
        List<CourseMember> teachers = List.of(
                CourseMember.builder().id(UUID.randomUUID()).course(course).user(teacher1).role(CourseRole.TEACHER).build(),
                CourseMember.builder().id(UUID.randomUUID()).course(course).user(teacher2).role(CourseRole.TEACHER).build()
        );
        when(courseMemberRepository.findByCourseIdAndRoleOrderByJoinedAtAsc(courseId, CourseRole.TEACHER))
                .thenReturn(teachers);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.notifyTeachers(courseId, "Title", "Message");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(n -> n.getUser().getId())
                .containsExactlyInAnyOrder(teacher1.getId(), teacher2.getId());
        assertThat(captor.getAllValues())
                .allMatch(n -> n.getType() == NotificationType.PEER_REVIEW);
    }

    @Test
    void getMyNotifications_returnsRepositoryResult() {
        List<Notification> expected = List.of(buildNotification(userId));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(expected);

        List<Notification> result = notificationService.getMyNotifications(userId);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void countUnread_returnsRepositoryResult() {
        when(notificationRepository.countByUserIdAndReadFalse(userId)).thenReturn(5L);

        long result = notificationService.countUnread(userId);

        assertThat(result).isEqualTo(5L);
    }

    @Test
    void markAsRead_marksNotificationForOwner() {
        Notification notification = buildNotification(userId);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(notificationId, userId);

        assertThat(notification.getRead()).isTrue();
        verify(notificationRepository).findById(notificationId);
    }

    @Test
    void markAsRead_throwsWhenNotificationNotFound() {
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(notificationId, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Notification not found");
    }

    @Test
    void markAsRead_throwsWhenNotOwner() {
        Notification notification = buildNotification(userId);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(notificationId, otherUserId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("only mark your own notifications");
    }
}

package com.classroom.core.service;

import com.classroom.core.dto.course.CourseDto;
import com.classroom.core.dto.invite.CreateInviteRequest;
import com.classroom.core.dto.invite.InviteDto;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.InviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InviteService {

    private final InviteRepository inviteRepository;
    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;

    public List<InviteDto> listInvites(UUID courseId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public InviteDto createInvite(UUID courseId, CreateInviteRequest request, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void revokeInvite(UUID courseId, UUID inviteId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public CourseDto joinCourse(String code, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}

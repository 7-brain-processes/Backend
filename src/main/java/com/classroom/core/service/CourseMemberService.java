package com.classroom.core.service;

import com.classroom.core.dto.member.MemberDto;
import com.classroom.core.model.CourseRole;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseMemberService {

    private final CourseMemberRepository courseMemberRepository;
    private final CourseRepository courseRepository;

    public Page<MemberDto> listMembers(UUID courseId, CourseRole role, Pageable pageable, UUID currentUserId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void removeMember(UUID courseId, UUID targetUserId, UUID currentUserId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void leaveCourse(UUID courseId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}

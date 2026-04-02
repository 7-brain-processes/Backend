package com.classroom.core.service;

import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.dto.team.PostCaptainDto;
import com.classroom.core.dto.team.SelectCaptainsResultDto;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.PostCaptainRepository;
import com.classroom.core.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCaptainService {

    private final PostCaptainRepository postCaptainRepository;
    private final PostRepository postRepository;
    private final CourseMemberRepository courseMemberRepository;

    public List<PostCaptainDto> getCaptains(UUID courseId, UUID postId, UUID userId) {
        Post post = getPostOrThrow(courseId, postId);
        ensureTeacherOrMember(courseId, userId);

        return postCaptainRepository.findByPostId(postId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public SelectCaptainsResultDto selectCaptains(UUID courseId, UUID postId, boolean reshuffle, UUID userId) {
        Post post = getPostOrThrow(courseId, postId);
        ensureTeacher(courseId, userId);

        if (post.getTeamFormationMode() != TeamFormationMode.RANDOM_CAPTAIN_SELECTION) {
            throw new BadRequestException("Post team formation mode must be RANDOM_CAPTAIN_SELECTION");
        }

        if (reshuffle) {
            postCaptainRepository.deleteByPostId(postId);
        } else if (!postCaptainRepository.findByPostId(postId).isEmpty()) {
            throw new BadRequestException("Captains already selected for this post");
        }

        List<User> candidates = getAvailableStudents(courseId, postId);
        if (candidates.isEmpty()) {
            throw new BadRequestException("No available students for captain selection");
        }

        int captainCount = calculateCaptainCount(candidates.size());
        List<User> selectedCaptains = selectRandomCaptains(candidates, captainCount);

        List<PostCaptain> captains = selectedCaptains.stream()
                .map(captain -> PostCaptain.builder()
                        .post(post)
                        .captain(captain)
                        .build())
                .collect(Collectors.toList());

        postCaptainRepository.saveAll(captains);

        List<PostCaptainDto> captainDtos = captains.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return SelectCaptainsResultDto.builder()
                .captains(captainDtos)
                .captainCount(captainCount)
                .build();
    }

    public int calculateCaptainCount(int studentCount) {
        return Math.max(1, (int) Math.ceil(studentCount / 5.0));
    }

    private List<User> getAvailableStudents(UUID courseId, UUID postId) {
        List<CourseMember> members = courseMemberRepository.findByCourseIdAndRole(courseId, CourseRole.STUDENT);
        return members.stream()
                .map(CourseMember::getUser)
                .collect(Collectors.toList());
    }

    private List<User> selectRandomCaptains(List<User> candidates, int count) {
        Collections.shuffle(candidates);
        return candidates.stream()
                .limit(Math.min(count, candidates.size()))
                .collect(Collectors.toList());
    }

    private Post getPostOrThrow(UUID courseId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!post.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Post not found in course");
        }

        return post;
    }

    private void ensureTeacher(UUID courseId, UUID userId) {
        CourseMember member = courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("Access denied"));

        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can perform this action");
        }
    }

    private void ensureTeacherOrMember(UUID courseId, UUID userId) {
        courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("Access denied"));
    }

    private PostCaptainDto toDto(PostCaptain captain) {
        return PostCaptainDto.builder()
                .id(captain.getId())
                .postId(captain.getPost().getId())
                .captainId(captain.getCaptain().getId())
                .captain(toUserDto(captain.getCaptain()))
                .assignedAt(captain.getAssignedAt())
                .build();
    }

    private UserDto toUserDto(User user) {
        return UserDto.from(user);
    }
    
}
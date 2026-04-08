package com.classroom.core.service;

import com.classroom.core.dto.team.PostCaptainDto;
import com.classroom.core.dto.team.SelectCaptainsRequest;
import com.classroom.core.dto.team.SelectCaptainsResultDto;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.Post;
import com.classroom.core.model.PostCaptain;
import com.classroom.core.model.TeamFormationMode;
import com.classroom.core.model.TeamRequirementTemplate;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.PostCaptainRepository;
import com.classroom.core.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final TeamRequirementTemplateService templateService;

    public List<PostCaptainDto> getCaptains(UUID courseId, UUID postId, UUID currentUserId) {
        ensureCourseMember(courseId, currentUserId);
        requireTaskPostInCourse(courseId, postId);

        return postCaptainRepository.findByPostIdWithUser(postId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public SelectCaptainsResultDto selectCaptains(UUID courseId, UUID postId, SelectCaptainsRequest request, UUID currentUserId) {
        Post post = getPostOrThrow(courseId, postId);
        ensureTeacher(courseId, currentUserId);

        if (post.getTeamFormationMode() != TeamFormationMode.CAPTAIN_SELECTION) {
            throw new BadRequestException("Captain selection is only available for CAPTAIN_SELECTION mode");
        }

        boolean reshuffle = Boolean.TRUE.equals(request.getReshuffle());
        if (!reshuffle && postCaptainRepository.countByPostId(postId) > 0) {
            throw new BadRequestException("Captains are already selected for this assignment. Set reshuffle=true to reselect");
        }

        if (reshuffle) {
            postCaptainRepository.deleteByPostId(postId);
        }

        List<CourseMember> students = courseMemberRepository
                .findByCourseIdAndRoleOrderByJoinedAtAsc(courseId, CourseRole.STUDENT);

        if (students.isEmpty()) {
            throw new BadRequestException("No students available for captain selection");
        }

        TeamRequirementTemplate template = templateService.getAppliedTemplate(post);
        int captainCount = calculateCaptainCount(students, template);

        List<CourseMember> captainCandidates = students;
        if (template != null && template.getRequiredCategory() != null) {
            UUID requiredCategoryId = template.getRequiredCategory().getId();
            captainCandidates = students.stream()
                    .filter(s -> s.getCategory() != null && s.getCategory().getId().equals(requiredCategoryId))
                    .collect(Collectors.toList());
            if (captainCandidates.isEmpty()) {
                throw new BadRequestException("No students with required category available for captain selection");
            }
        }

        List<CourseMember> selectedCaptains = selectRandomCaptains(captainCandidates, captainCount);

        List<PostCaptain> captains = selectedCaptains.stream()
                .map(student -> PostCaptain.builder()
                        .post(post)
                        .user(student.getUser())
                        .build())
                .collect(Collectors.toList());

        postCaptainRepository.saveAll(captains);

        List<PostCaptainDto> captainDtos = captains.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return SelectCaptainsResultDto.builder()
                .selectedCaptains(captainCount)
                .captains(captainDtos)
                .selectedAt(Instant.now())
                .build();
    }

    int calculateCaptainCount(List<CourseMember> students, TeamRequirementTemplate template) {
        int studentCount = students.size();
        int limit = studentCount;

        if (template != null && template.getRequiredCategory() != null) {
            UUID requiredCategoryId = template.getRequiredCategory().getId();
            long studentsInCategory = students.stream()
                    .filter(s -> s.getCategory() != null && s.getCategory().getId().equals(requiredCategoryId))
                    .count();
            limit = (int) studentsInCategory;
        }

        if (template != null && template.getMinTeamSize() != null && template.getMinTeamSize() > 0) {
            int bySize = studentCount / template.getMinTeamSize();
            limit = Math.min(limit, bySize);
        } else if (template == null || template.getRequiredCategory() == null) {
        
            limit = (int) Math.ceil(studentCount / 5.0);
        }

        return Math.max(1, limit);
    }

    private List<CourseMember> selectRandomCaptains(List<CourseMember> students, int count) {
        Collections.shuffle(students);
        return students.stream().limit(count).collect(Collectors.toList());
    }

    private PostCaptainDto toDto(PostCaptain captain) {
        return PostCaptainDto.builder()
                .id(captain.getId())
                .userId(captain.getUser().getId())
                .username(captain.getUser().getUsername())
                .displayName(captain.getUser().getDisplayName())
                .build();
    }

    private Post getPostOrThrow(UUID courseId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (!post.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Post not found in course");
        }
        return post;
    }

    private void ensureTeacher(UUID courseId, UUID currentUserId) {
        boolean isTeacher = courseMemberRepository.existsByCourseIdAndUserIdAndRole(courseId, currentUserId, CourseRole.TEACHER);
        if (!isTeacher) {
            throw new ForbiddenException("Only teachers can manage team formation");
        }
    }

    private void ensureCourseMember(UUID courseId, UUID currentUserId) {
        if (!courseMemberRepository.existsByCourseIdAndUserId(courseId, currentUserId)) {
            throw new ForbiddenException("You are not a member of this course");
        }
    }

    private Post requireTaskPostInCourse(UUID courseId, UUID postId) {
        Post post = getPostOrThrow(courseId, postId);
        if (post.getType() != com.classroom.core.model.PostType.TASK) {
            throw new BadRequestException("Team formation is only available for task posts");
        }
        return post;
    }
}
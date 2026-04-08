package com.classroom.core.service;

import com.classroom.core.dto.course.CourseCategoryDto;
import com.classroom.core.dto.team.AutoFormationStudentDto;
import com.classroom.core.dto.team.AutoTeamFormationRequest;
import com.classroom.core.dto.team.AutoTeamFormationResultDto;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.Course;
import com.classroom.core.model.CourseCategory;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.CourseTeam;
import com.classroom.core.model.Post;
import com.classroom.core.model.PostType;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.CourseTeamRepository;
import com.classroom.core.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamFormationService {

    private static final int DEFAULT_MIN_TEAM_SIZE = 2;
    private static final int DEFAULT_MAX_TEAM_SIZE = 4;

    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final CourseTeamRepository courseTeamRepository;
    private final PostRepository postRepository;

    public List<AutoFormationStudentDto> listAvailableStudents(UUID courseId, UUID postId, UUID currentUserId) {
        ensureTeacher(courseId, currentUserId);
        requireTaskPostInCourse(courseId, postId);

        List<CourseMember> students = courseMemberRepository
                .findByCourseIdAndRoleOrderByJoinedAtAsc(courseId, CourseRole.STUDENT);

        return students.stream()
                .filter(member -> courseMemberRepository
                        .countStudentTeamsInPost(courseId, member.getUser().getId(), postId) == 0)
                .map(member -> AutoFormationStudentDto.builder()
                        .userId(member.getUser().getId())
                        .username(member.getUser().getUsername())
                        .displayName(member.getUser().getDisplayName())
                        .category(toCategoryDto(member.getCategory()))
                        .build())
                .toList();
    }

    @Transactional
    public AutoTeamFormationResultDto runAutomaticFormation(UUID courseId,
                                                            UUID postId,
                                                            AutoTeamFormationRequest request,
                                                            UUID currentUserId) {
        Course course = getCourseOrThrow(courseId);
        ensureTeacher(courseId, currentUserId);
        Post post = requireTaskPostInCourse(courseId, postId);

        int minTeamSize = request.getMinTeamSize() == null ? DEFAULT_MIN_TEAM_SIZE : request.getMinTeamSize();
        int maxTeamSize = request.getMaxTeamSize() == null ? DEFAULT_MAX_TEAM_SIZE : request.getMaxTeamSize();

        if (minTeamSize > maxTeamSize) {
            throw new BadRequestException("minTeamSize must be less than or equal to maxTeamSize");
        }

        List<CourseTeam> existingPostTeams = courseTeamRepository.findByPostId(postId);
        boolean reshuffle = Boolean.TRUE.equals(request.getReshuffle());

        if (!existingPostTeams.isEmpty() && !reshuffle) {
            throw new BadRequestException("Teams are already formed for this assignment. Set reshuffle=true to recompute");
        }

        if (reshuffle && !existingPostTeams.isEmpty()) {
            removeExistingPostTeams(courseId, existingPostTeams);
        }

        List<CourseMember> students = courseMemberRepository
                .findByCourseIdAndRoleOrderByJoinedAtAsc(courseId, CourseRole.STUDENT);

        if (students.isEmpty()) {
            throw new BadRequestException("No students available for team formation");
        }

        int lowerBoundTeams = (int) Math.ceil((double) students.size() / maxTeamSize);
        int upperBoundTeams = students.size() / minTeamSize;

        if (upperBoundTeams == 0 || lowerBoundTeams > upperBoundTeams) {
            throw new BadRequestException("Cannot form teams with provided min/max constraints");
        }

        int teamCount = lowerBoundTeams;

        List<List<CourseMember>> buckets = initBuckets(teamCount);
        if (Boolean.TRUE.equals(request.getBalanceByCategory())) {
            distributeGrouped(students, buckets, member -> member.getCategory() == null ? null : member.getCategory().getId());
        } else if (Boolean.TRUE.equals(request.getBalanceByRole())) {
            distributeGrouped(students, buckets, CourseMember::getRole);
        } else {
            distributeSimple(students, buckets);
        }

        if (buckets.stream().anyMatch(team -> team.size() < minTeamSize || team.size() > maxTeamSize)) {
            throw new BadRequestException("Could not satisfy min/max team size constraints");
        }

        int formedTeams = 0;
        for (int i = 0; i < buckets.size(); i++) {
            List<CourseMember> teamMembers = buckets.get(i);
            if (teamMembers.isEmpty()) {
                continue;
            }

            CourseTeam team = courseTeamRepository.save(CourseTeam.builder()
                    .course(course)
                    .post(post)
                    .name("Auto Team " + (i + 1))
                    .maxSize(maxTeamSize)
                    .selfEnrollmentEnabled(false)
                    .categories(collectTeamCategories(teamMembers))
                    .build());

            for (CourseMember member : teamMembers) {
                member.setTeam(team);
            }
            courseMemberRepository.saveAll(teamMembers);
            formedTeams++;
        }

        return AutoTeamFormationResultDto.builder()
                .formedTeams(formedTeams)
                .assignedStudents(students.size())
                .unassignedStudents(0)
                .generatedAt(Instant.now())
                .build();
    }

    public AutoTeamFormationResultDto getLastAutomaticFormationResult(UUID courseId,
                                                                      UUID postId,
                                                                      UUID currentUserId) {
        ensureTeacher(courseId, currentUserId);
        requireTaskPostInCourse(courseId, postId);

        List<CourseTeam> teams = courseTeamRepository.findByPostId(postId);
        int assignedStudents = teams.stream()
                .mapToInt(team -> courseMemberRepository.countByTeamId(team.getId()))
                .sum();

        int allStudents = courseMemberRepository
                .findByCourseIdAndRoleOrderByJoinedAtAsc(courseId, CourseRole.STUDENT)
                .size();

        Instant generatedAt = teams.stream()
                .map(CourseTeam::getCreatedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return AutoTeamFormationResultDto.builder()
                .formedTeams(teams.size())
                .assignedStudents(assignedStudents)
                .unassignedStudents(Math.max(allStudents - assignedStudents, 0))
                .generatedAt(generatedAt)
                .build();
    }

    private List<List<CourseMember>> initBuckets(int teamCount) {
        List<List<CourseMember>> buckets = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) {
            buckets.add(new ArrayList<>());
        }
        return buckets;
    }

    private void distributeSimple(List<CourseMember> students, List<List<CourseMember>> buckets) {
        int teamCount = buckets.size();
        for (int i = 0; i < students.size(); i++) {
            buckets.get(i % teamCount).add(students.get(i));
        }
    }

    private <T> void distributeGrouped(List<CourseMember> students,
                                       List<List<CourseMember>> buckets,
                                       Function<CourseMember, T> keySelector) {
        Map<T, List<CourseMember>> grouped = new LinkedHashMap<>();
        for (CourseMember member : students) {
            grouped.computeIfAbsent(keySelector.apply(member), unused -> new ArrayList<>()).add(member);
        }

        int teamIndex = 0;
        int teamCount = buckets.size();
        for (List<CourseMember> group : grouped.values()) {
            for (CourseMember member : group) {
                buckets.get(teamIndex % teamCount).add(member);
                teamIndex++;
            }
        }
    }

    private void removeExistingPostTeams(UUID courseId, List<CourseTeam> existingPostTeams) {
        for (CourseTeam team : existingPostTeams) {
            List<CourseMember> members = courseMemberRepository
                    .findByCourseIdAndTeamIdOrderByJoinedAtAsc(courseId, team.getId());
            for (CourseMember member : members) {
                member.setTeam(null);
            }
            courseMemberRepository.saveAll(members);
        }

        courseTeamRepository.deleteAll(existingPostTeams);
    }

    private Set<CourseCategory> collectTeamCategories(List<CourseMember> members) {
        Set<CourseCategory> categories = new LinkedHashSet<>();
        for (CourseMember member : members) {
            if (member.getCategory() != null) {
                categories.add(member.getCategory());
            }
        }
        return categories;
    }

    private Course getCourseOrThrow(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
    }

    private Post requireTaskPostInCourse(UUID courseId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!post.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Post not found");
        }

        if (post.getType() != PostType.TASK) {
            throw new BadRequestException("Automatic team formation is available only for task posts");
        }

        return post;
    }

    private CourseMember ensureTeacher(UUID courseId, UUID userId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course not found");
        }

        CourseMember member = courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));

        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can run automatic team formation");
        }

        return member;
    }

    private CourseCategoryDto toCategoryDto(CourseCategory category) {
        if (category == null) {
            return null;
        }

        return CourseCategoryDto.builder()
                .id(category.getId())
                .title(category.getTitle())
                .description(category.getDescription())
                .active(category.isActive())
                .createdAt(category.getCreatedAt())
                .build();
    }
}

package com.classroom.core.service;

import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.dto.course.CourseCategoryDto;
import com.classroom.core.dto.team.CourseTeamDto;
import com.classroom.core.dto.team.CourseTeamMemberDto;
import com.classroom.core.dto.team.CreateCourseTeamRequest;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.DuplicateResourceException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.Course;
import com.classroom.core.model.CourseCategory;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.CourseTeam;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.CourseTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseTeamService {

    private final CourseTeamRepository courseTeamRepository;
    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;

    public List<CourseTeamDto> listTeams(UUID courseId, UUID currentUserId) {
        getCourseOrThrow(courseId);
        ensureMember(courseId, currentUserId);

        List<CourseTeam> teams = courseTeamRepository.findByCourseIdOrderByCreatedAtAsc(courseId);

        return teams.stream()
                .map(team -> {
                    List<CourseMember> members = courseMemberRepository
                            .findByCourseIdAndTeamIdOrderByJoinedAtAsc(courseId, team.getId());
                    return toTeamDto(team, members);
                })
                .toList();
    }

    @Transactional
    public CourseTeamDto createTeam(UUID courseId, CreateCourseTeamRequest request, UUID currentUserId) {
        Course course = getCourseOrThrow(courseId);
        ensureTeacher(courseId, currentUserId);

        String normalizedName = request.getName().trim();
        if (courseTeamRepository.existsByCourseIdAndNameIgnoreCase(courseId, normalizedName)) {
            throw new DuplicateResourceException("Team with this name already exists");
        }

        CourseTeam team = courseTeamRepository.save(CourseTeam.builder()
                .course(course)
                .name(normalizedName)
                .build());

        List<CourseMember> members = assignMembers(courseId, team, request.getMemberIds());
        return toTeamDto(team, members);
    }

    private List<CourseMember> assignMembers(UUID courseId, CourseTeam team, List<UUID> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }

        List<UUID> distinctMemberIds = new ArrayList<>(new LinkedHashSet<>(memberIds));
        List<CourseMember> members = courseMemberRepository.findByCourseIdAndUserIdIn(courseId, distinctMemberIds);

        if (members.size() != distinctMemberIds.size()) {
            throw new ResourceNotFoundException("One or more members not found in this course");
        }

        for (CourseMember member : members) {
            if (member.getRole() != CourseRole.STUDENT) {
                throw new BadRequestException("Only students can be assigned to teams");
            }

            if (member.getTeam() != null) {
                throw new DuplicateResourceException("One or more members are already assigned to a team");
            }

            member.setTeam(team);
        }

        return courseMemberRepository.saveAll(members);
    }

    private Course getCourseOrThrow(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
    }

    private CourseMember ensureMember(UUID courseId, UUID userId) {
        return courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));
    }

    private CourseMember ensureTeacher(UUID courseId, UUID userId) {
        CourseMember member = ensureMember(courseId, userId);

        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can manage teams");
        }

        return member;
    }

    private CourseTeamDto toTeamDto(CourseTeam team, List<CourseMember> members) {
        List<CourseTeamMemberDto> memberDtos = members.stream()
                .map(this::toMemberDto)
                .toList();

        return CourseTeamDto.builder()
                .id(team.getId())
                .name(team.getName())
                .createdAt(team.getCreatedAt())
                .membersCount(memberDtos.size())
                .members(memberDtos)
                .build();
    }

    private CourseTeamMemberDto toMemberDto(CourseMember member) {
        CourseCategory category = member.getCategory();

        return CourseTeamMemberDto.builder()
                .user(UserDto.from(member.getUser()))
                .category(category == null ? null : CourseCategoryDto.builder()
                        .id(category.getId())
                        .title(category.getTitle())
                        .description(category.getDescription())
                        .active(category.isActive())
                        .createdAt(category.getCreatedAt())
                        .build())
                .build();
    }
}

package com.classroom.core.service;

import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.dto.course.CourseCategoryDto;
import com.classroom.core.dto.team.CourseTeamAvailabilityDto;
import com.classroom.core.dto.team.CourseTeamDto;
import com.classroom.core.dto.team.CourseTeamMemberDto;
import com.classroom.core.dto.team.CreateCourseTeamRequest;
import com.classroom.core.dto.team.EnrollmentResponseDto;
import com.classroom.core.dto.team.StudentTeamDto;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.DuplicateResourceException;
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
import com.classroom.core.repository.CourseCategoryRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.CourseTeamRepository;
import com.classroom.core.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseTeamService {

    private final CourseTeamRepository courseTeamRepository;
    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final PostRepository postRepository;
    private final CourseCategoryRepository courseCategoryRepository;

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
        if (courseTeamRepository.existsByCourseIdAndPostIsNullAndNameIgnoreCase(courseId, normalizedName)) {
            throw new DuplicateResourceException("Team with this name already exists");
        }

        Set<CourseCategory> categories = resolveTeamCategories(courseId, request.getCategoryIds());

        CourseTeam team = courseTeamRepository.save(CourseTeam.builder()
                .course(course)
                .name(normalizedName)
                .maxSize(request.getMaxSize())
                .selfEnrollmentEnabled(Boolean.TRUE.equals(request.getSelfEnrollmentEnabled()))
                .categories(categories)
                .build());

        List<CourseMember> members = assignMembers(courseId, team, request.getMemberIds());
        return toTeamDto(team, members);
    }

    @Transactional
    public CourseTeamDto createTeamForPost(UUID courseId,
                                           UUID postId,
                                           CreateCourseTeamRequest request,
                                           UUID currentUserId) {
        Course course = getCourseOrThrow(courseId);
        ensureTeacher(courseId, currentUserId);
        Post post = getPostOrThrow(courseId, postId);
        if (post.getType() != PostType.TASK) {
            throw new BadRequestException("Teams can only be created for task posts");
        }

        String normalizedName = request.getName().trim();
        if (courseTeamRepository.existsByCourseIdAndPostIdAndNameIgnoreCase(courseId, postId, normalizedName)) {
            throw new DuplicateResourceException("Team with this name already exists in this assignment");
        }

        Set<CourseCategory> categories = resolveTeamCategories(courseId, request.getCategoryIds());

        CourseTeam team = courseTeamRepository.save(CourseTeam.builder()
                .course(course)
                .post(post)
                .name(normalizedName)
                .maxSize(request.getMaxSize())
                .selfEnrollmentEnabled(Boolean.TRUE.equals(request.getSelfEnrollmentEnabled()))
                .categories(categories)
                .build());

        List<CourseMember> members = assignMembers(courseId, team, request.getMemberIds());
        return toTeamDto(team, members);
    }

    private Set<CourseCategory> resolveTeamCategories(UUID courseId, List<UUID> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        Set<CourseCategory> categories = new LinkedHashSet<>();
        for (UUID categoryId : new LinkedHashSet<>(categoryIds)) {
            CourseCategory category = courseCategoryRepository.findByIdAndCourseId(categoryId, courseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found in this course: " + categoryId));
            categories.add(category);
        }

        return categories;
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

        List<CourseCategoryDto> teamCategories = team.getCategories().stream()
                .map(category -> CourseCategoryDto.builder()
                        .id(category.getId())
                        .title(category.getTitle())
                        .description(category.getDescription())
                        .active(category.isActive())
                        .createdAt(category.getCreatedAt())
                        .build())
                .toList();

        return CourseTeamDto.builder()
                .id(team.getId())
                .name(team.getName())
                .createdAt(team.getCreatedAt())
                .membersCount(memberDtos.size())
                .members(memberDtos)
                .maxSize(team.getMaxSize())
                .selfEnrollmentEnabled(team.isSelfEnrollmentEnabled())
                .isFull(team.getMaxSize() != null && memberDtos.size() >= team.getMaxSize())
                .categories(teamCategories)
                .categoryId(teamCategories.isEmpty() ? null : teamCategories.get(0).getId())
                .categoryTitle(teamCategories.isEmpty() ? null : teamCategories.get(0).getTitle())
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


    public List<CourseTeamAvailabilityDto> listTeamsForEnrollment(UUID courseId, UUID postId, UUID studentUserId) {
        getCourseOrThrow(courseId);
        CourseMember student = ensureMember(courseId, studentUserId);
        
        if (student.getRole() != CourseRole.STUDENT) {
            throw new ForbiddenException("Only students can view available teams");
        }

        getPostOrThrow(courseId, postId);
        
        List<CourseTeam> teams = courseTeamRepository
            .findByPostIdOrderByCreatedAtAsc(postId);

        UUID studentTeamId = findStudentTeamInPost(courseId, studentUserId, postId)
                .map(CourseMember::getTeam)
                .map(CourseTeam::getId)
                .orElse(null);

        return teams.stream()
                .map(team -> {
                    int memberCount = courseMemberRepository.countByTeamId(team.getId());
                    boolean isFull = team.getMaxSize() != null && memberCount >= team.getMaxSize();
                    boolean isStudentMember = team.getId().equals(studentTeamId);

                    List<CourseCategoryDto> teamCategories = team.getCategories().stream()
                            .map(category -> CourseCategoryDto.builder()
                                    .id(category.getId())
                                    .title(category.getTitle())
                                    .description(category.getDescription())
                                    .active(category.isActive())
                                    .createdAt(category.getCreatedAt())
                                    .build())
                            .toList();

                    return CourseTeamAvailabilityDto.builder()
                            .id(team.getId())
                            .name(team.getName())
                            .currentMembers(memberCount)
                            .maxSize(team.getMaxSize())
                            .selfEnrollmentEnabled(team.isSelfEnrollmentEnabled())
                            .isFull(isFull)
                            .isStudentMember(isStudentMember)
                            .categories(teamCategories)
                            .createdAt(team.getCreatedAt())
                            .build();
                })
                .toList();
    }


    public StudentTeamDto getStudentTeamInPost(UUID courseId, UUID postId, UUID studentUserId) {
        getCourseOrThrow(courseId);
        CourseMember student = ensureMember(courseId, studentUserId);
        
        if (student.getRole() != CourseRole.STUDENT) {
            throw new ForbiddenException("Only students can view team information");
        }

        getPostOrThrow(courseId, postId);

        CourseMember teamMember = findStudentTeamInPost(courseId, studentUserId, postId)
                .orElseThrow(() -> new ResourceNotFoundException("Student is not in any team for this assignment"));

        CourseTeam team = teamMember.getTeam();
        List<CourseMember> members = courseMemberRepository
                .findByCourseIdAndTeamIdOrderByJoinedAtAsc(courseId, team.getId());

        List<CourseTeamMemberDto> memberDtos = members.stream()
                .map(this::toMemberDto)
                .toList();

        return StudentTeamDto.builder()
                .teamId(team.getId())
                .teamName(team.getName())
                .membersCount(memberDtos.size())
                .maxSize(team.getMaxSize())
                .members(memberDtos)
                .joinedAt(teamMember.getJoinedAt())
                .build();
    }

    @Transactional
    public EnrollmentResponseDto enrollStudentInTeam(UUID courseId, UUID postId, UUID teamId, UUID studentUserId) {
        getCourseOrThrow(courseId);
        CourseMember student = ensureMember(courseId, studentUserId);
        
        if (student.getRole() != CourseRole.STUDENT) {
            throw new ForbiddenException("Only students can enroll in teams");
        }

        getPostOrThrow(courseId, postId);
        
        CourseTeam team = courseTeamRepository.findByPostIdAndId(postId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found or not available for self-enrollment"));

        if (!team.isSelfEnrollmentEnabled()) {
            throw new BadRequestException("Self-enrollment is not enabled for this team");
        }

        validateStudentEnrollment(courseId, postId, teamId, student);

        if (courseMemberRepository.findByCourseIdAndUserIdAndTeamId(courseId, studentUserId, teamId).isPresent()) {
            throw new DuplicateResourceException("Student is already a member of this team");
        }

        student.setTeam(team);
        courseMemberRepository.save(student);

        int memberCount = courseMemberRepository.countByTeamId(teamId);
        boolean isFull = team.getMaxSize() != null && memberCount >= team.getMaxSize();

        return EnrollmentResponseDto.builder()
                .success(true)
                .message("Successfully enrolled in team: " + team.getName())
                .team(EnrollmentResponseDto.TeamEnrollmentStatusDto.builder()
                        .teamId(team.getId().toString())
                        .teamName(team.getName())
                        .currentMembers(memberCount)
                        .maxSize(team.getMaxSize())
                        .build())
                .build();
    }

   
    @Transactional
    public EnrollmentResponseDto removeStudentFromTeam(UUID courseId, UUID postId, UUID teamId, UUID studentUserId) {
        getCourseOrThrow(courseId);
        CourseMember student = ensureMember(courseId, studentUserId);
        
        if (student.getRole() != CourseRole.STUDENT) {
            throw new ForbiddenException("Only students can leave teams");
        }

        getPostOrThrow(courseId, postId);
        
        CourseTeam team = courseTeamRepository.findByPostIdAndId(postId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        CourseMember membership = courseMemberRepository
                .findByCourseIdAndUserIdAndTeamId(courseId, studentUserId, teamId)
                .orElseThrow(() -> new BadRequestException("Student is not a member of this team"));

        membership.setTeam(null);
        courseMemberRepository.save(membership);

        int memberCount = courseMemberRepository.countByTeamId(teamId);

        return EnrollmentResponseDto.builder()
                .success(true)
                .message("Successfully left team: " + team.getName())
                .team(EnrollmentResponseDto.TeamEnrollmentStatusDto.builder()
                        .teamId(team.getId().toString())
                        .teamName(team.getName())
                        .currentMembers(memberCount)
                        .maxSize(team.getMaxSize())
                        .build())
                .build();
    }


    private void validateStudentEnrollment(UUID courseId, UUID postId, UUID teamId, CourseMember student) {
        CourseTeam team = courseTeamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        if (team.getMaxSize() != null) {
            int memberCount = courseMemberRepository.countByTeamId(teamId);
            if (memberCount >= team.getMaxSize()) {
                throw new BadRequestException("Team is full. Maximum size: " + team.getMaxSize());
            }
        }

        int existingTeamCount = courseMemberRepository.countStudentTeamsInPost(courseId, student.getUser().getId(), postId);
        if (existingTeamCount > 0) {
            throw new BadRequestException("Student is already enrolled in another team for this assignment");
        }

        if (!team.getCategories().isEmpty()) {
            CourseCategory studentCategory = student.getCategory();
            if (studentCategory == null || team.getCategories().stream().noneMatch(c -> c.getId().equals(studentCategory.getId()))) {
                throw new BadRequestException("Student category does not match any allowed team category");
            }
        }

    }


    private Optional<CourseMember> findStudentTeamInPost(UUID courseId, UUID userId, UUID postId) {
        return courseMemberRepository.findStudentTeamInPost(courseId, userId, postId);
    }

    private Post getPostOrThrow(UUID courseId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post (assignment) not found"));

        if (!post.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Post (assignment) not found");
        }

        return post;
    }
}


package com.classroom.core.service;

import com.classroom.core.dto.course.CourseCategoryDto;
import com.classroom.core.dto.team.ApplyTeamRequirementTemplateRequest;
import com.classroom.core.dto.team.CreateTeamRequirementTemplateRequest;
import com.classroom.core.dto.team.TeamRequirementTemplateDto;
import com.classroom.core.dto.team.TemplateApplyResultDto;
import com.classroom.core.dto.team.UpdateTeamRequirementTemplateRequest;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.CourseCategory;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.CourseTeam;
import com.classroom.core.model.Post;
import com.classroom.core.model.PostType;
import com.classroom.core.model.TeamFormationMode;
import com.classroom.core.model.TeamRequirementTemplate;
import com.classroom.core.repository.CourseCategoryRepository;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.CourseTeamRepository;
import com.classroom.core.repository.PostRepository;
import com.classroom.core.repository.TeamRequirementTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamRequirementTemplateService {

    private final TeamRequirementTemplateRepository templateRepository;
    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final CourseCategoryRepository courseCategoryRepository;
    private final PostRepository postRepository;
    private final CourseTeamRepository courseTeamRepository;

    public List<TeamRequirementTemplateDto> listTemplates(UUID courseId, UUID currentUserId) {
        ensureTeacher(courseId, currentUserId);

        return templateRepository.findByCourseIdOrderByCreatedAtAsc(courseId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public TeamRequirementTemplateDto getTemplate(UUID courseId, UUID templateId, UUID currentUserId) {
        ensureTeacher(courseId, currentUserId);

        TeamRequirementTemplate template = templateRepository.findByIdAndCourseId(templateId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        return toDto(template);
    }

    @Transactional
    public TeamRequirementTemplateDto createTemplate(UUID courseId,
                                                     CreateTeamRequirementTemplateRequest request,
                                                     UUID currentUserId) {
        ensureTeacher(courseId, currentUserId);
        validateMinMax(request.getMinTeamSize(), request.getMaxTeamSize());

        TeamRequirementTemplate template = TeamRequirementTemplate.builder()
                .course(com.classroom.core.model.Course.builder().id(courseId).build())
                .name(request.getName().trim())
                .description(request.getDescription())
                .minTeamSize(request.getMinTeamSize())
                .maxTeamSize(request.getMaxTeamSize())
                .requiredCategory(resolveCategory(courseId, request.getRequiredCategoryId()))
                .requireAudio(Boolean.TRUE.equals(request.getRequireAudio()))
                .requireVideo(Boolean.TRUE.equals(request.getRequireVideo()))
                .active(true)
                .build();

        template = templateRepository.save(template);
        return toDto(template);
    }

    @Transactional
    public TeamRequirementTemplateDto updateTemplate(UUID courseId,
                                                     UUID templateId,
                                                     UpdateTeamRequirementTemplateRequest request,
                                                     UUID currentUserId) {
        ensureTeacher(courseId, currentUserId);

        TeamRequirementTemplate template = templateRepository.findByIdAndCourseId(templateId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        Integer minSize = request.getMinTeamSize() == null ? template.getMinTeamSize() : request.getMinTeamSize();
        Integer maxSize = request.getMaxTeamSize() == null ? template.getMaxTeamSize() : request.getMaxTeamSize();
        validateMinMax(minSize, maxSize);

        if (request.getName() != null) {
            template.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            template.setDescription(request.getDescription());
        }
        if (request.getMinTeamSize() != null) {
            template.setMinTeamSize(request.getMinTeamSize());
        }
        if (request.getMaxTeamSize() != null) {
            template.setMaxTeamSize(request.getMaxTeamSize());
        }
        if (request.getRequiredCategoryId() != null) {
            template.setRequiredCategory(resolveCategory(courseId, request.getRequiredCategoryId()));
        }
        if (request.getRequireAudio() != null) {
            template.setRequireAudio(request.getRequireAudio());
        }
        if (request.getRequireVideo() != null) {
            template.setRequireVideo(request.getRequireVideo());
        }
        if (request.getActive() != null) {
            template.setActive(request.getActive());
            template.setArchivedAt(request.getActive() ? null : Instant.now());
        }

        template = templateRepository.save(template);
        return toDto(template);
    }

    @Transactional
    public void archiveTemplate(UUID courseId, UUID templateId, UUID currentUserId) {
        ensureTeacher(courseId, currentUserId);

        TeamRequirementTemplate template = templateRepository.findByIdAndCourseId(templateId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        template.setActive(false);
        template.setArchivedAt(Instant.now());
        templateRepository.save(template);
    }

    @Transactional
    public TemplateApplyResultDto applyTemplate(UUID courseId,
                                                UUID templateId,
                                                ApplyTeamRequirementTemplateRequest request,
                                                UUID currentUserId) {
        ensureTeacher(courseId, currentUserId);

        TeamRequirementTemplate template = templateRepository.findByIdAndCourseId(templateId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!post.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Post not found");
        }

        if (post.getType() != PostType.TASK) {
            throw new BadRequestException("Template can be applied only to task posts");
        }

        if (!template.isActive()) {
            throw new BadRequestException("Template is archived");
        }

        validateCompatibility(courseId, post, template);
        TeamFormationMode appliedMode = applyTemplateToPost(post, template);

        return TemplateApplyResultDto.builder()
                .postId(post.getId())
                .templateId(template.getId())
                .appliedMode(appliedMode)
                .build();
    }

    private void validateCompatibility(UUID courseId, Post post, TeamRequirementTemplate template) {
        List<CourseMember> students = courseMemberRepository
                .findByCourseIdAndRoleOrderByJoinedAtAsc(courseId, CourseRole.STUDENT);

        if (students.isEmpty()) {
            throw new BadRequestException("No students in course to apply template");
        }

        Integer minTeamSize = template.getMinTeamSize();
        Integer maxTeamSize = template.getMaxTeamSize();
        if (minTeamSize != null && minTeamSize > students.size()) {
            throw new BadRequestException("Template minTeamSize is incompatible with current student count");
        }
        if (maxTeamSize != null && maxTeamSize < 1) {
            throw new BadRequestException("Template maxTeamSize must be greater than 0");
        }

        CourseCategory requiredCategory = template.getRequiredCategory();
        if (requiredCategory != null) {
            boolean categoryExistsAmongStudents = students.stream()
                    .anyMatch(member -> member.getCategory() != null
                            && requiredCategory.getId().equals(member.getCategory().getId()));

            if (!categoryExistsAmongStudents) {
                throw new BadRequestException("Template required category has no students in this course");
            }
        }

        List<CourseTeam> existingPostTeams = courseTeamRepository.findByPostId(post.getId());
        for (CourseTeam team : existingPostTeams) {
            int membersCount = courseMemberRepository.countByTeamId(team.getId());

            if (maxTeamSize != null && membersCount > maxTeamSize) {
                throw new BadRequestException("Existing team size is incompatible with template maxTeamSize");
            }

            if (requiredCategory != null) {
                List<CourseMember> teamMembers = courseMemberRepository
                        .findByCourseIdAndTeamIdOrderByJoinedAtAsc(courseId, team.getId());

                boolean hasIncompatibleMember = teamMembers.stream()
                        .anyMatch(member -> member.getCategory() == null
                                || !requiredCategory.getId().equals(member.getCategory().getId()));
                if (hasIncompatibleMember) {
                    throw new BadRequestException("Existing teams are incompatible with template required category");
                }
            }
        }
    }

    private TeamFormationMode applyTemplateToPost(Post post, TeamRequirementTemplate template) {
        TeamFormationMode resultingMode = post.getTeamFormationMode() == null
                ? TeamFormationMode.DRAFT
                : post.getTeamFormationMode();

        if (resultingMode == TeamFormationMode.FREE) {
            resultingMode = TeamFormationMode.DRAFT;
        }

        post.setTeamFormationMode(resultingMode);
        post.setTeamRequirementTemplate(template);
        postRepository.save(post);

        List<CourseTeam> existingPostTeams = courseTeamRepository.findByPostId(post.getId());
        for (CourseTeam team : existingPostTeams) {
            team.setMaxSize(template.getMaxTeamSize());
            if (template.getRequiredCategory() == null) {
                team.setCategories(new LinkedHashSet<>());
            } else {
                team.setCategories(new LinkedHashSet<>(List.of(template.getRequiredCategory())));
            }
        }

        if (!existingPostTeams.isEmpty()) {
            courseTeamRepository.saveAll(existingPostTeams);
        }

        return resultingMode;
    }

    public TeamRequirementTemplate getAppliedTemplate(Post post) {
        return post.getTeamRequirementTemplate();
    }

    private void ensureTeacher(UUID courseId, UUID userId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course not found");
        }

        CourseMember member = courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));

        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can manage templates");
        }
    }

    private CourseCategory resolveCategory(UUID courseId, UUID categoryId) {
        if (categoryId == null) {
            return null;
        }

        return courseCategoryRepository.findByIdAndCourseId(categoryId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private void validateMinMax(Integer minTeamSize, Integer maxTeamSize) {
        if (minTeamSize != null && maxTeamSize != null && minTeamSize > maxTeamSize) {
            throw new BadRequestException("minTeamSize must be less than or equal to maxTeamSize");
        }
    }

    private TeamRequirementTemplateDto toDto(TeamRequirementTemplate template) {
        CourseCategory category = template.getRequiredCategory();

        return TeamRequirementTemplateDto.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .minTeamSize(template.getMinTeamSize())
                .maxTeamSize(template.getMaxTeamSize())
                .requiredCategory(category == null ? null : CourseCategoryDto.builder()
                        .id(category.getId())
                        .title(category.getTitle())
                        .description(category.getDescription())
                        .active(category.isActive())
                        .createdAt(category.getCreatedAt())
                        .build())
                .requireAudio(template.isRequireAudio())
                .requireVideo(template.isRequireVideo())
                .active(template.isActive())
                .createdAt(template.getCreatedAt())
                .archivedAt(template.getArchivedAt())
                .build();
    }
}

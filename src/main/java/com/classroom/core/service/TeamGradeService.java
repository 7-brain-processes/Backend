package com.classroom.core.service;

import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.dto.team.SetTeamGradeDistributionModeRequest;
import com.classroom.core.dto.team.StudentDistributedGradeDto;
import com.classroom.core.dto.team.TeamGradeDistributionDto;
import com.classroom.core.dto.team.TeamGradeDto;
import com.classroom.core.dto.team.UpsertTeamGradeRequest;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.CourseTeam;
import com.classroom.core.model.Post;
import com.classroom.core.model.PostType;
import com.classroom.core.model.TeamGrade;
import com.classroom.core.model.TeamGradeDistributionMode;
import com.classroom.core.model.TeamStudentGrade;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.CourseTeamRepository;
import com.classroom.core.repository.PostRepository;
import com.classroom.core.repository.TeamGradeRepository;
import com.classroom.core.repository.TeamStudentGradeRepository;
import com.classroom.core.repository.TeamGradeVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamGradeService {

    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final PostRepository postRepository;
    private final CourseTeamRepository courseTeamRepository;
    private final TeamGradeRepository teamGradeRepository;
    private final TeamStudentGradeRepository teamStudentGradeRepository;
    private final TeamGradeVoteRepository teamGradeVoteRepository;

    public TeamGradeDto getTeamGrade(UUID courseId, UUID postId, UUID teamId, UUID currentUserId) {
        ensureTeacher(courseId, currentUserId);
        requireTaskPostInCourse(courseId, postId);
        requireTeamInCourse(courseId, teamId);

        TeamGrade grade = teamGradeRepository.findByPostIdAndTeamId(postId, teamId)
                .orElse(null);

        if (grade == null) {
            return TeamGradeDto.builder()
                    .postId(postId)
                    .teamId(teamId)
                    .distributionMode(TeamGradeDistributionMode.MANUAL)
                    .build();
        }

        return toDto(grade);
    }

    @Transactional
    public TeamGradeDto upsertTeamGrade(UUID courseId, UUID postId, UUID teamId,
                                        UpsertTeamGradeRequest request, UUID currentUserId) {
        ensureTeacher(courseId, currentUserId);
        Post post = requireTaskPostInCourse(courseId, postId);
        CourseTeam team = requireTeamInCourse(courseId, teamId);

        TeamGrade grade = teamGradeRepository.findByPostIdAndTeamId(postId, teamId)
                .orElse(TeamGrade.builder()
                        .post(post)
                        .team(team)
                        .distributionMode(TeamGradeDistributionMode.MANUAL)
                        .build());

        grade.setGrade(request.getGrade());
        grade.setComment(request.getComment());

        TeamGrade saved = teamGradeRepository.save(grade);
        if (saved.getDistributionMode() == TeamGradeDistributionMode.AUTO_EQUAL) {
            ensurePersistedAutoDistribution(courseId, saved, true);
        }

        return toDto(saved);
    }

    public TeamGradeDistributionDto getDistribution(UUID courseId, UUID postId, UUID teamId, UUID currentUserId) {
        ensureTeacher(courseId, currentUserId);
        requireTaskPostInCourse(courseId, postId);
        requireTeamInCourse(courseId, teamId);

        TeamGrade grade = teamGradeRepository.findByPostIdAndTeamId(postId, teamId)
                .orElse(null);

        TeamGradeDistributionMode mode = grade == null || grade.getDistributionMode() == null
                ? TeamGradeDistributionMode.MANUAL
                : grade.getDistributionMode();

        Integer teamGrade = grade == null ? null : grade.getGrade();

        List<CourseMember> members = courseMemberRepository.findByCourseIdAndTeamIdOrderByJoinedAtAsc(courseId, teamId);
        List<StudentDistributedGradeDto> students;

        if (mode == TeamGradeDistributionMode.AUTO_EQUAL && grade != null) {
            ensurePersistedAutoDistribution(courseId, grade, false);
            students = teamStudentGradeRepository.findByTeamGradeIdOrderByStudentIdAsc(grade.getId())
                .stream()
                .map(entry -> StudentDistributedGradeDto.builder()
                    .student(UserDto.from(entry.getStudent()))
                    .grade(entry.getGrade())
                    .build())
                .toList();
        } else if ((mode == TeamGradeDistributionMode.CAPTAIN_MANUAL
                || mode == TeamGradeDistributionMode.TEAM_VOTE) && grade != null) {
            students = teamStudentGradeRepository.findByTeamGradeIdOrderByStudentIdAsc(grade.getId())
                .stream()
                .map(entry -> StudentDistributedGradeDto.builder()
                    .student(UserDto.from(entry.getStudent()))
                    .grade(entry.getGrade())
                    .build())
                .toList();
            if (students.isEmpty()) {
                students = distribute(members, teamGrade, TeamGradeDistributionMode.MANUAL);
            }
        } else {
            students = distribute(members, teamGrade, mode);
        }

        return TeamGradeDistributionDto.builder()
                .teamId(teamId)
                .teamGrade(teamGrade)
                .distributionMode(mode)
                .students(students)
                .build();
    }

    @Transactional
    public TeamGradeDistributionDto setDistributionMode(UUID courseId, UUID postId, UUID teamId,
                                                        SetTeamGradeDistributionModeRequest request,
                                                        UUID currentUserId) {
        ensureTeacher(courseId, currentUserId);
        Post post = requireTaskPostInCourse(courseId, postId);
        CourseTeam team = requireTeamInCourse(courseId, teamId);

        TeamGrade grade = teamGradeRepository.findByPostIdAndTeamId(postId, teamId)
                .orElse(TeamGrade.builder()
                        .post(post)
                        .team(team)
                        .distributionMode(TeamGradeDistributionMode.MANUAL)
                        .build());

        grade.setDistributionMode(request.getDistributionMode());
        TeamGrade saved = teamGradeRepository.save(grade);

       teamGradeVoteRepository.deleteByTeamGradeId(saved.getId());

        if (saved.getDistributionMode() == TeamGradeDistributionMode.AUTO_EQUAL) {
            ensurePersistedAutoDistribution(courseId, saved, true);
        } else {
            teamStudentGradeRepository.deleteByTeamGradeId(saved.getId());
            teamStudentGradeRepository.flush();
        }

        return getDistribution(courseId, postId, teamId, currentUserId);
    }

    private void ensurePersistedAutoDistribution(UUID courseId, TeamGrade grade, boolean forceRecompute) {
        List<CourseMember> members = courseMemberRepository
                .findByCourseIdAndTeamIdOrderByJoinedAtAsc(courseId, grade.getTeam().getId());

        Map<UUID, TeamStudentGrade> existingByStudent = teamStudentGradeRepository
                .findByTeamGradeIdOrderByStudentIdAsc(grade.getId())
                .stream()
                .collect(Collectors.toMap(entry -> entry.getStudent().getId(), entry -> entry));

        boolean needsRecompute = existingByStudent.size() != members.size()
                || members.stream().anyMatch(member -> !existingByStudent.containsKey(member.getUser().getId()));

        if (!needsRecompute && !forceRecompute) {
            return;
        }

        List<StudentDistributedGradeDto> distributed = distribute(members, grade.getGrade(), TeamGradeDistributionMode.AUTO_EQUAL);

        teamStudentGradeRepository.deleteByTeamGradeId(grade.getId());
        teamStudentGradeRepository.flush();

        List<TeamStudentGrade> persisted = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            CourseMember member = members.get(i);
            Integer distributedGrade = distributed.get(i).getGrade();

            persisted.add(TeamStudentGrade.builder()
                    .teamGrade(grade)
                    .student(member.getUser())
                    .grade(distributedGrade)
                    .build());
        }

        teamStudentGradeRepository.saveAll(persisted);
    }

    private List<StudentDistributedGradeDto> distribute(List<CourseMember> members,
                                                        Integer teamGrade,
                                                        TeamGradeDistributionMode mode) {
        if (members.isEmpty()) {
            return List.of();
        }

        if (teamGrade == null) {
            return members.stream()
                    .map(member -> StudentDistributedGradeDto.builder()
                            .student(UserDto.from(member.getUser()))
                            .grade(null)
                            .build())
                    .toList();
        }

        if (mode == TeamGradeDistributionMode.AUTO_EQUAL) {
            int size = members.size();
            int base = teamGrade / size;
            int remainder = teamGrade % size;

            List<StudentDistributedGradeDto> result = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                int value = base + (i < remainder ? 1 : 0);
                result.add(StudentDistributedGradeDto.builder()
                        .student(UserDto.from(members.get(i).getUser()))
                        .grade(value)
                        .build());
            }
            return result;
        }

        return members.stream()
                .map(member -> StudentDistributedGradeDto.builder()
                        .student(UserDto.from(member.getUser()))
                        .grade(null)
                        .build())
                .toList();
    }

    private void ensureTeacher(UUID courseId, UUID userId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course not found");
        }

        CourseMember member = courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));

        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can manage team grades");
        }
    }

    private Post requireTaskPostInCourse(UUID courseId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!post.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Post not found");
        }

        if (post.getType() != PostType.TASK) {
            throw new BadRequestException("Team grading is available only for task posts");
        }

        return post;
    }

    private CourseTeam requireTeamInCourse(UUID courseId, UUID teamId) {
        return courseTeamRepository.findByIdAndCourseId(teamId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));
    }

    private TeamGradeDto toDto(TeamGrade grade) {
        return TeamGradeDto.builder()
                .id(grade.getId())
                .postId(grade.getPost().getId())
                .teamId(grade.getTeam().getId())
                .grade(grade.getGrade())
                .comment(grade.getComment())
                .distributionMode(grade.getDistributionMode())
                .updatedAt(grade.getUpdatedAt())
                .build();
    }
}

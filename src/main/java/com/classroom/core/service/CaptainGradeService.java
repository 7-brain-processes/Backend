package com.classroom.core.service;

import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.dto.team.CaptainGradeDistributionRequest;
import com.classroom.core.dto.team.CaptainStudentGradeEntry;
import com.classroom.core.dto.team.StudentDistributedGradeDto;
import com.classroom.core.dto.team.TeamGradeDistributionDto;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CaptainGradeService {

    private final PostCaptainRepository postCaptainRepository;
    private final PostRepository postRepository;
    private final CourseTeamRepository courseTeamRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final TeamGradeRepository teamGradeRepository;
    private final TeamStudentGradeRepository teamStudentGradeRepository;

    public TeamGradeDistributionDto getDistributionForm(UUID courseId, UUID postId, UUID captainId) {
        ensureIsCaptain(postId, captainId);
        requireTaskPostInCourse(courseId, postId);

        CourseTeam team = courseTeamRepository.findByPostIdAndCaptainId(postId, captainId)
                .orElseThrow(() -> new ResourceNotFoundException("Captain's team not found"));

        TeamGrade teamGrade = teamGradeRepository.findByPostIdAndTeamId(postId, team.getId())
                .orElseThrow(() -> new BadRequestException("Team grade has not been set yet"));

        if (teamGrade.getDistributionMode() != TeamGradeDistributionMode.CAPTAIN_MANUAL) {
            throw new BadRequestException("Captain grade distribution is not enabled for this post");
        }

        List<CourseMember> members = courseMemberRepository
                .findByCourseIdAndTeamIdOrderByJoinedAtAsc(courseId, team.getId());

        Map<UUID, Integer> gradeByStudent = teamStudentGradeRepository
                .findByTeamGradeIdOrderByStudentIdAsc(teamGrade.getId())
                .stream()
                .collect(Collectors.toMap(g -> g.getStudent().getId(), TeamStudentGrade::getGrade));

        List<StudentDistributedGradeDto> students = members.stream()
                .map(m -> StudentDistributedGradeDto.builder()
                        .student(UserDto.from(m.getUser()))
                        .grade(gradeByStudent.get(m.getUser().getId()))
                        .build())
                .collect(Collectors.toList());

        return TeamGradeDistributionDto.builder()
                .teamId(team.getId())
                .teamGrade(teamGrade.getGrade())
                .distributionMode(TeamGradeDistributionMode.CAPTAIN_MANUAL)
                .students(students)
                .build();
    }

    @Transactional
    public TeamGradeDistributionDto saveDistribution(UUID courseId, UUID postId,
                                                     CaptainGradeDistributionRequest request, UUID captainId) {
        ensureIsCaptain(postId, captainId);
        requireTaskPostInCourse(courseId, postId);

        CourseTeam team = courseTeamRepository.findByPostIdAndCaptainId(postId, captainId)
                .orElseThrow(() -> new ResourceNotFoundException("Captain's team not found"));

        TeamGrade teamGrade = teamGradeRepository.findByPostIdAndTeamId(postId, team.getId())
                .orElseThrow(() -> new BadRequestException("Team grade has not been set yet"));

        if (teamGrade.getDistributionMode() != TeamGradeDistributionMode.CAPTAIN_MANUAL) {
            throw new BadRequestException("Captain grade distribution is not enabled for this post");
        }

        if (teamGrade.getGrade() == null) {
            throw new BadRequestException("Team grade has not been set yet");
        }

        List<CourseMember> members = courseMemberRepository
                .findByCourseIdAndTeamIdOrderByJoinedAtAsc(courseId, team.getId());

        Set<UUID> memberIds = members.stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());

        Set<UUID> requestIds = request.getGrades().stream()
                .map(CaptainStudentGradeEntry::getStudentId)
                .collect(Collectors.toSet());

        if (request.getGrades().size() != requestIds.size()) {
            throw new BadRequestException("Duplicate student entries in distribution");
        }

        if (!requestIds.equals(memberIds)) {
            throw new BadRequestException("Grade entries must cover all team members exactly");
        }

        int sum = request.getGrades().stream().mapToInt(CaptainStudentGradeEntry::getGrade).sum();
        if (sum != teamGrade.getGrade()) {
            throw new BadRequestException(
                    "Sum of individual grades (" + sum + ") must equal team grade (" + teamGrade.getGrade() + ")");
        }

        teamStudentGradeRepository.deleteByTeamGradeId(teamGrade.getId());
        teamStudentGradeRepository.flush();

        Map<UUID, Integer> gradeMap = request.getGrades().stream()
                .collect(Collectors.toMap(CaptainStudentGradeEntry::getStudentId, CaptainStudentGradeEntry::getGrade));

        List<TeamStudentGrade> toSave = members.stream()
                .map(m -> TeamStudentGrade.builder()
                        .teamGrade(teamGrade)
                        .student(m.getUser())
                        .grade(gradeMap.get(m.getUser().getId()))
                        .build())
                .collect(Collectors.toList());

        teamStudentGradeRepository.saveAll(toSave);

        List<StudentDistributedGradeDto> students = members.stream()
                .map(m -> StudentDistributedGradeDto.builder()
                        .student(UserDto.from(m.getUser()))
                        .grade(gradeMap.get(m.getUser().getId()))
                        .build())
                .collect(Collectors.toList());

        return TeamGradeDistributionDto.builder()
                .teamId(team.getId())
                .teamGrade(teamGrade.getGrade())
                .distributionMode(TeamGradeDistributionMode.CAPTAIN_MANUAL)
                .students(students)
                .build();
    }

    private void ensureIsCaptain(UUID postId, UUID captainId) {
        if (!postCaptainRepository.existsByPostIdAndUserId(postId, captainId)) {
            throw new ForbiddenException("User is not a captain for this post");
        }
    }

    private void requireTaskPostInCourse(UUID courseId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (!post.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Post not found");
        }
        if (post.getType() != PostType.TASK) {
            throw new BadRequestException("Grade distribution is available only for task posts");
        }
    }
}

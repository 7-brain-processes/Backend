package com.classroom.core.service;

import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.dto.team.CaptainGradeDistributionRequest;
import com.classroom.core.dto.team.CaptainStudentGradeEntry;
import com.classroom.core.dto.team.StudentDistributedGradeDto;
import com.classroom.core.dto.team.TeamGradeDistributionDto;
import com.classroom.core.dto.team.VoteStatusDto;
import com.classroom.core.dto.team.VoterStatusDto;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GradeVotingService {

    private final PostRepository postRepository;
    private final CourseTeamRepository courseTeamRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final TeamGradeRepository teamGradeRepository;
    private final TeamStudentGradeRepository teamStudentGradeRepository;
    private final TeamGradeVoteRepository voteRepository;

    
    public VoteStatusDto getVoteStatus(UUID courseId, UUID postId, UUID userId) {
        requireTaskPostInCourse(courseId, postId);
        CourseMember membership = courseMemberRepository.findStudentTeamInPost(courseId, userId, postId)
                .orElseThrow(() -> new ForbiddenException("You are not in a team for this post"));
        CourseTeam team = membership.getTeam();

        TeamGrade teamGrade = requireVoteMode(postId, team.getId());
        return buildVoteStatus(courseId, team, teamGrade, userId);
    }

    @Transactional
    public VoteStatusDto submitVote(UUID courseId, UUID postId, CaptainGradeDistributionRequest request, UUID userId) {
        requireTaskPostInCourse(courseId, postId);
        CourseMember membership = courseMemberRepository.findStudentTeamInPost(courseId, userId, postId)
                .orElseThrow(() -> new ForbiddenException("You are not in a team for this post"));
        CourseTeam team = membership.getTeam();

        TeamGrade teamGrade = requireVoteMode(postId, team.getId());

        if (teamGrade.getGrade() == null) {
            throw new BadRequestException("Team grade has not been set yet");
        }

        if (voteRepository.existsByTeamGradeIdAndVoterId(teamGrade.getId(), userId)) {
            throw new BadRequestException("You have already submitted your vote");
        }

        List<CourseMember> members = courseMemberRepository
                .findByCourseIdAndTeamIdOrderByJoinedAtAsc(courseId, team.getId());

        validateDistribution(request, members, teamGrade.getGrade());

        User voter = membership.getUser();
        TeamGradeVote vote = TeamGradeVote.builder()
                .teamGrade(teamGrade)
                .voter(voter)
                .build();

        Map<UUID, Integer> gradeMap = request.getGrades().stream()
                .collect(Collectors.toMap(CaptainStudentGradeEntry::getStudentId, CaptainStudentGradeEntry::getGrade));

        List<TeamGradeVoteEntry> entries = members.stream()
                .map(m -> TeamGradeVoteEntry.builder()
                        .vote(vote)
                        .student(m.getUser())
                        .grade(gradeMap.get(m.getUser().getId()))
                        .build())
                .collect(Collectors.toList());

        vote.setEntries(entries);
        voteRepository.save(vote);

       
        long voteCount = voteRepository.countByTeamGradeId(teamGrade.getId());
        if (voteCount >= members.size()) {
            List<TeamGradeVote> allVotes = voteRepository.findByTeamGradeIdWithEntries(teamGrade.getId());
            List<StudentDistributedGradeDto> distribution =
                    computeFinalDistribution(members, allVotes, teamGrade.getGrade());
            saveStudentGrades(teamGrade, members, distribution);
        }

        return buildVoteStatus(courseId, team, teamGrade, userId);
    }

    
    public VoteStatusDto getTeamVoteStatus(UUID courseId, UUID postId, UUID teamId, UUID teacherId) {
        ensureTeacher(courseId, teacherId);
        requireTaskPostInCourse(courseId, postId);
        CourseTeam team = requireTeamInCourse(courseId, teamId);
        TeamGrade teamGrade = requireVoteMode(postId, teamId);
        return buildVoteStatus(courseId, team, teamGrade, null);
    }

   
    @Transactional
    public TeamGradeDistributionDto finalizeVoting(UUID courseId, UUID postId, UUID teamId, UUID teacherId) {
        ensureTeacher(courseId, teacherId);
        requireTaskPostInCourse(courseId, postId);
        requireTeamInCourse(courseId, teamId);

        TeamGrade teamGrade = requireVoteMode(postId, teamId);

        if (teamGrade.getGrade() == null) {
            throw new BadRequestException("Team grade has not been set yet");
        }

        List<TeamGradeVote> votes = voteRepository.findByTeamGradeIdWithEntries(teamGrade.getId());
        if (votes.isEmpty()) {
            throw new BadRequestException("No votes have been submitted yet");
        }

        List<CourseMember> members = courseMemberRepository
                .findByCourseIdAndTeamIdOrderByJoinedAtAsc(courseId, teamId);
        List<StudentDistributedGradeDto> distribution =
                computeFinalDistribution(members, votes, teamGrade.getGrade());
        saveStudentGrades(teamGrade, members, distribution);

        return TeamGradeDistributionDto.builder()
                .teamId(teamId)
                .teamGrade(teamGrade.getGrade())
                .distributionMode(TeamGradeDistributionMode.TEAM_VOTE)
                .students(distribution)
                .build();
    }

   
    private TeamGrade requireVoteMode(UUID postId, UUID teamId) {
        TeamGrade teamGrade = teamGradeRepository.findByPostIdAndTeamId(postId, teamId)
                .orElseThrow(() -> new BadRequestException("Team grade has not been set yet"));
        if (teamGrade.getDistributionMode() != TeamGradeDistributionMode.TEAM_VOTE) {
            throw new BadRequestException("Team vote distribution is not enabled for this post");
        }
        return teamGrade;
    }

    private void validateDistribution(CaptainGradeDistributionRequest request,
                                       List<CourseMember> members, int teamGrade) {
        Set<UUID> memberIds = members.stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());

        Set<UUID> requestIds = request.getGrades().stream()
                .map(CaptainStudentGradeEntry::getStudentId)
                .collect(Collectors.toSet());

        if (request.getGrades().size() != requestIds.size()) {
            throw new BadRequestException("Duplicate student entries in vote");
        }
        if (!requestIds.equals(memberIds)) {
            throw new BadRequestException("Vote must cover all team members exactly");
        }

        int sum = request.getGrades().stream().mapToInt(CaptainStudentGradeEntry::getGrade).sum();
        if (sum != teamGrade) {
            throw new BadRequestException(
                    "Sum of voted grades (" + sum + ") must equal team grade (" + teamGrade + ")");
        }
    }

    List<StudentDistributedGradeDto> computeFinalDistribution(
            List<CourseMember> members, List<TeamGradeVote> votes, int teamGrade) {

                
        Map<UUID, Long> totalByStudent = new LinkedHashMap<>();
        for (CourseMember m : members) {
            totalByStudent.put(m.getUser().getId(), 0L);
        }
        for (TeamGradeVote vote : votes) {
            for (TeamGradeVoteEntry entry : vote.getEntries()) {
                totalByStudent.merge(entry.getStudent().getId(), (long) entry.getGrade(), Long::sum);
            }
        }

        int voteCount = votes.size();

        Map<UUID, Integer> floorGrades = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> e : totalByStudent.entrySet()) {
            floorGrades.put(e.getKey(), (int) (e.getValue() / voteCount));
        }
        int sumFloor = floorGrades.values().stream().mapToInt(Integer::intValue).sum();
        int remainder = teamGrade - sumFloor;

        List<StudentDistributedGradeDto> result = new ArrayList<>();
        int r = remainder;
        for (CourseMember m : members) {
            int grade = floorGrades.get(m.getUser().getId()) + (r > 0 ? 1 : 0);
            if (r > 0) r--;
            result.add(StudentDistributedGradeDto.builder()
                    .student(UserDto.from(m.getUser()))
                    .grade(grade)
                    .build());
        }
        return result;
    }

    private void saveStudentGrades(TeamGrade teamGrade, List<CourseMember> members,
                                   List<StudentDistributedGradeDto> distribution) {
        teamStudentGradeRepository.deleteByTeamGradeId(teamGrade.getId());
        teamStudentGradeRepository.flush();

        Map<UUID, Integer> gradeByStudent = distribution.stream()
                .collect(Collectors.toMap(d -> d.getStudent().getId(), StudentDistributedGradeDto::getGrade));

        List<TeamStudentGrade> toSave = members.stream()
                .map(m -> TeamStudentGrade.builder()
                        .teamGrade(teamGrade)
                        .student(m.getUser())
                        .grade(gradeByStudent.get(m.getUser().getId()))
                        .build())
                .collect(Collectors.toList());

        teamStudentGradeRepository.saveAll(toSave);
    }

    private VoteStatusDto buildVoteStatus(UUID courseId, CourseTeam team, TeamGrade teamGrade, UUID currentUserId) {
        List<CourseMember> members = courseMemberRepository
                .findByCourseIdAndTeamIdOrderByJoinedAtAsc(courseId, team.getId());

        List<TeamGradeVote> allVotes = voteRepository.findByTeamGradeIdWithEntries(teamGrade.getId());
        Set<UUID> votedUserIds = allVotes.stream()
                .map(v -> v.getVoter().getId())
                .collect(Collectors.toSet());

        List<VoterStatusDto> voters = members.stream()
                .map(m -> VoterStatusDto.builder()
                        .user(UserDto.from(m.getUser()))
                        .hasVoted(votedUserIds.contains(m.getUser().getId()))
                        .build())
                .collect(Collectors.toList());

        List<StudentDistributedGradeDto> myVote = null;
        if (currentUserId != null) {
            Optional<TeamGradeVote> ownVote = allVotes.stream()
                    .filter(v -> v.getVoter().getId().equals(currentUserId))
                    .findFirst();
            if (ownVote.isPresent()) {
                Map<UUID, Integer> entryMap = ownVote.get().getEntries().stream()
                        .collect(Collectors.toMap(e -> e.getStudent().getId(), TeamGradeVoteEntry::getGrade));
                myVote = members.stream()
                        .map(m -> StudentDistributedGradeDto.builder()
                                .student(UserDto.from(m.getUser()))
                                .grade(entryMap.get(m.getUser().getId()))
                                .build())
                        .collect(Collectors.toList());
            }
        }

        
        List<TeamStudentGrade> saved = teamStudentGradeRepository
                .findByTeamGradeIdOrderByStudentIdAsc(teamGrade.getId());
        boolean finalized = !saved.isEmpty();

        List<StudentDistributedGradeDto> finalDistribution = null;
        if (finalized) {
            Map<UUID, Integer> savedGrades = saved.stream()
                    .collect(Collectors.toMap(g -> g.getStudent().getId(), TeamStudentGrade::getGrade));
            finalDistribution = members.stream()
                    .map(m -> StudentDistributedGradeDto.builder()
                            .student(UserDto.from(m.getUser()))
                            .grade(savedGrades.get(m.getUser().getId()))
                            .build())
                    .collect(Collectors.toList());
        }

        return VoteStatusDto.builder()
                .teamId(team.getId())
                .teamGrade(teamGrade.getGrade())
                .finalized(finalized)
                .voters(voters)
                .myVote(myVote)
                .finalDistribution(finalDistribution)
                .build();
    }

    private void ensureTeacher(UUID courseId, UUID userId) {
        CourseMember member = courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));
        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can perform this action");
        }
    }

    private void requireTaskPostInCourse(UUID courseId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (!post.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Post not found");
        }
        if (post.getType() != PostType.TASK) {
            throw new BadRequestException("Grade voting is available only for task posts");
        }
    }

    private CourseTeam requireTeamInCourse(UUID courseId, UUID teamId) {
        return courseTeamRepository.findByIdAndCourseId(teamId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));
    }
}

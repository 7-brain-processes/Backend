package com.classroom.core.support;

import com.classroom.core.repository.AssessmentCriterionGradeRepository;
import com.classroom.core.repository.AssessmentResultRepository;
import com.classroom.core.repository.CriterionRepository;
import com.classroom.core.repository.GradingConfigRepository;
import com.classroom.core.repository.CommentRepository;
import com.classroom.core.repository.CourseCategoryRepository;
import com.classroom.core.repository.PeerReviewAssignmentRepository;
import com.classroom.core.repository.PeerReviewConfigRepository;
import com.classroom.core.repository.PeerReviewPenaltyRepository;
import com.classroom.core.repository.PeerReviewRepository;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.CourseTeamRepository;
import com.classroom.core.repository.GradingConfigVersionRepository;
import com.classroom.core.repository.InviteRepository;
import com.classroom.core.repository.PostFileRepository;
import com.classroom.core.repository.PostRepository;
import com.classroom.core.repository.SolutionFileRepository;
import com.classroom.core.repository.SolutionRepository;
import com.classroom.core.repository.TeamGradeRepository;
import com.classroom.core.repository.TeamRequirementTemplateRepository;
import com.classroom.core.repository.TeamStudentGradeRepository;
import com.classroom.core.repository.UserRepository;
import com.classroom.core.repository.VersionedCriterionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TestDatabaseCleaner {

    private final AssessmentCriterionGradeRepository assessmentCriterionGradeRepository;
    private final AssessmentResultRepository assessmentResultRepository;
    private final PeerReviewRepository peerReviewRepository;
    private final PeerReviewAssignmentRepository peerReviewAssignmentRepository;
    private final PeerReviewPenaltyRepository peerReviewPenaltyRepository;
    private final PeerReviewConfigRepository peerReviewConfigRepository;
    private final CriterionRepository criterionRepository;
    private final GradingConfigRepository gradingConfigRepository;
    private final VersionedCriterionRepository versionedCriterionRepository;
    private final GradingConfigVersionRepository gradingConfigVersionRepository;
    private final CommentRepository commentRepository;
    private final SolutionFileRepository solutionFileRepository;
    private final PostFileRepository postFileRepository;
    private final TeamStudentGradeRepository teamStudentGradeRepository;
    private final TeamGradeRepository teamGradeRepository;
    private final SolutionRepository solutionRepository;
    private final TeamRequirementTemplateRepository teamRequirementTemplateRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final CourseTeamRepository courseTeamRepository;
    private final InviteRepository inviteRepository;
    private final PostRepository postRepository;
    private final CourseCategoryRepository courseCategoryRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    @Transactional
    public void clean() {
        assessmentCriterionGradeRepository.deleteAll();
        assessmentCriterionGradeRepository.flush();

        assessmentResultRepository.deleteAll();
        assessmentResultRepository.flush();

        peerReviewRepository.deleteAll();
        peerReviewRepository.flush();

        peerReviewAssignmentRepository.deleteAll();
        peerReviewAssignmentRepository.flush();

        peerReviewPenaltyRepository.deleteAll();
        peerReviewPenaltyRepository.flush();

        peerReviewConfigRepository.deleteAll();
        peerReviewConfigRepository.flush();

        criterionRepository.deleteAll();
        criterionRepository.flush();

        gradingConfigRepository.deleteAll();
        gradingConfigRepository.flush();

        versionedCriterionRepository.deleteAll();
        versionedCriterionRepository.flush();

        gradingConfigVersionRepository.deleteAll();
        gradingConfigVersionRepository.flush();

        commentRepository.deleteAll();
        commentRepository.flush();

        solutionFileRepository.deleteAll();
        solutionFileRepository.flush();

        postFileRepository.deleteAll();
        postFileRepository.flush();

        teamStudentGradeRepository.deleteAll();
        teamStudentGradeRepository.flush();

        teamGradeRepository.deleteAll();
        teamGradeRepository.flush();

        solutionRepository.deleteAll();
        solutionRepository.flush();

        teamRequirementTemplateRepository.deleteAll();
        teamRequirementTemplateRepository.flush();

        courseMemberRepository.deleteAll();
        courseMemberRepository.flush();

        courseTeamRepository.deleteAll();
        courseTeamRepository.flush();

        inviteRepository.deleteAll();
        inviteRepository.flush();

        postRepository.deleteAll();
        postRepository.flush();

        courseCategoryRepository.deleteAll();
        courseCategoryRepository.flush();

        courseRepository.deleteAll();
        courseRepository.flush();

        userRepository.deleteAll();
        userRepository.flush();
    }
}

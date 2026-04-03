package com.classroom.core.support;

import com.classroom.core.repository.CommentRepository;
import com.classroom.core.repository.CourseCategoryRepository;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.CourseTeamRepository;
import com.classroom.core.repository.InviteRepository;
import com.classroom.core.repository.PostFileRepository;
import com.classroom.core.repository.PostRepository;
import com.classroom.core.repository.SolutionFileRepository;
import com.classroom.core.repository.SolutionRepository;
import com.classroom.core.repository.TeamGradeRepository;
import com.classroom.core.repository.TeamRequirementTemplateRepository;
import com.classroom.core.repository.TeamStudentGradeRepository;
import com.classroom.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TestDatabaseCleaner {

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

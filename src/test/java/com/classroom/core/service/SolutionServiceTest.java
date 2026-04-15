package com.classroom.core.service;

import com.classroom.core.dto.solution.CreateSolutionRequest;
import com.classroom.core.dto.solution.GradeRequest;
import com.classroom.core.dto.solution.SolutionDto;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.DuplicateResourceException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.PostRepository;
import com.classroom.core.repository.SolutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SolutionServiceTest {

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CourseMemberRepository courseMemberRepository;

    @InjectMocks
    private SolutionService solutionService;

    private final UUID courseId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private final UUID solutionId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();

    private Course buildCourse() {
        return Course.builder().id(courseId).name("Test").createdAt(Instant.now()).build();
    }

    private User buildUser(UUID id) {
        return User.builder().id(id).username("user").passwordHash("h").createdAt(Instant.now()).build();
    }

    private CourseMember buildMember(Course course, UUID uid, CourseRole role) {
        return CourseMember.builder()
                .id(UUID.randomUUID()).course(course).user(buildUser(uid))
                .role(role).joinedAt(Instant.now()).build();
    }

    private Post buildTaskPost(Course course) {
        return Post.builder()
                .id(postId).course(course).author(buildUser(teacherId))
                .title("Task 1").type(PostType.TASK)
                .deadline(Instant.now().plusSeconds(86400))
                .files(new ArrayList<>()).comments(new ArrayList<>())
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private Post buildMaterialPost(Course course) {
        return Post.builder()
                .id(postId).course(course).author(buildUser(teacherId))
                .title("Lecture").type(PostType.MATERIAL)
                .files(new ArrayList<>()).comments(new ArrayList<>())
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private Solution buildSolution(Post post) {
        return Solution.builder()
                .id(solutionId).post(post).student(buildUser(studentId))
                .text("My answer").status(SolutionStatus.SUBMITTED)
                .files(new ArrayList<>()).comments(new ArrayList<>())
                .submittedAt(Instant.now()).updatedAt(Instant.now()).build();
    }
    @Test
    void createSolution_createsForStudentOnTaskPost() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, studentId, CourseRole.STUDENT);
        Post task = buildTaskPost(course);
        CreateSolutionRequest request = new CreateSolutionRequest();
        request.setText("My solution");

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(student));
        when(postRepository.findById(postId)).thenReturn(Optional.of(task));
        when(solutionRepository.existsByPostIdAndStudentId(postId, studentId)).thenReturn(false);
        when(solutionRepository.save(any(Solution.class))).thenAnswer(inv -> {
            Solution s = inv.getArgument(0);
            s.setId(solutionId);
            s.setSubmittedAt(Instant.now());
            s.setUpdatedAt(Instant.now());
            return s;
        });

        SolutionDto result = solutionService.createSolution(courseId, postId, request, studentId);

        assertThat(result.getText()).isEqualTo("My solution");
        assertThat(result.getStatus()).isEqualTo(SolutionStatus.SUBMITTED);
        verify(solutionRepository).save(any(Solution.class));
    }

    @Test
    void createSolution_throwsBadRequestForMaterialPost() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, studentId, CourseRole.STUDENT);
        Post material = buildMaterialPost(course);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(student));
        when(postRepository.findById(postId)).thenReturn(Optional.of(material));

        CreateSolutionRequest request = new CreateSolutionRequest();
        request.setText("Answer");

        assertThatThrownBy(() -> solutionService.createSolution(courseId, postId, request, studentId))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createSolution_throwsConflictWhenAlreadySubmitted() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, studentId, CourseRole.STUDENT);
        Post task = buildTaskPost(course);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(student));
        when(postRepository.findById(postId)).thenReturn(Optional.of(task));
        when(solutionRepository.existsByPostIdAndStudentId(postId, studentId)).thenReturn(true);

        CreateSolutionRequest request = new CreateSolutionRequest();
        request.setText("Duplicate");

        assertThatThrownBy(() -> solutionService.createSolution(courseId, postId, request, studentId))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createSolution_throwsForbiddenForTeacher() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, teacherId, CourseRole.TEACHER);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, teacherId))
                .thenReturn(Optional.of(teacher));

        CreateSolutionRequest request = new CreateSolutionRequest();
        request.setText("Teacher answer");

        assertThatThrownBy(() -> solutionService.createSolution(courseId, postId, request, teacherId))
                .isInstanceOf(ForbiddenException.class);
    }
    @Test
    void listSolutions_returnsPaginatedForTeacher() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, teacherId, CourseRole.TEACHER);
        Post task = buildTaskPost(course);
        Solution solution = buildSolution(task);
        Pageable pageable = PageRequest.of(0, 20);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, teacherId))
                .thenReturn(Optional.of(teacher));
        when(postRepository.findById(postId)).thenReturn(Optional.of(task));
        when(solutionRepository.findByPostId(postId, pageable))
                .thenReturn(new PageImpl<>(List.of(solution)));

        Page<SolutionDto> result = solutionService.listSolutions(courseId, postId, null, pageable, teacherId);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void listSolutions_filtersByStatus() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, teacherId, CourseRole.TEACHER);
        Post task = buildTaskPost(course);
        Pageable pageable = PageRequest.of(0, 20);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, teacherId))
                .thenReturn(Optional.of(teacher));
        when(postRepository.findById(postId)).thenReturn(Optional.of(task));
        when(solutionRepository.findByPostIdAndStatus(postId, SolutionStatus.GRADED, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        Page<SolutionDto> result = solutionService.listSolutions(
                courseId, postId, SolutionStatus.GRADED, pageable, teacherId);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void listSolutions_throwsForbiddenForStudent() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, studentId, CourseRole.STUDENT);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(student));

        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> solutionService.listSolutions(courseId, postId, null, pageable, studentId))
                .isInstanceOf(ForbiddenException.class);
    }
    @Test
    void getMySolution_returnsStudentsSolution() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, studentId, CourseRole.STUDENT);
        Post task = buildTaskPost(course);
        Solution solution = buildSolution(task);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(student));
        when(postRepository.findById(postId)).thenReturn(Optional.of(task));
        when(solutionRepository.findByPostIdAndStudentId(postId, studentId))
                .thenReturn(Optional.of(solution));

        SolutionDto result = solutionService.getMySolution(courseId, postId, studentId);

        assertThat(result.getId()).isEqualTo(solutionId);
        assertThat(result.getText()).isEqualTo("My answer");
    }

    @Test
    void getMySolution_throwsNotFoundWhenNoSolution() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, studentId, CourseRole.STUDENT);
        Post task = buildTaskPost(course);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(student));
        when(postRepository.findById(postId)).thenReturn(Optional.of(task));
        when(solutionRepository.findByPostIdAndStudentId(postId, studentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> solutionService.getMySolution(courseId, postId, studentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
    @Test
    void getSolution_returnsForTeacher() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, teacherId, CourseRole.TEACHER);
        Post task = buildTaskPost(course);
        Solution solution = buildSolution(task);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, teacherId))
                .thenReturn(Optional.of(teacher));
        when(postRepository.findById(postId)).thenReturn(Optional.of(task));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));

        SolutionDto result = solutionService.getSolution(courseId, postId, solutionId, teacherId);

        assertThat(result.getId()).isEqualTo(solutionId);
    }

    @Test
    void getSolution_returnsForOwningStudent() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, studentId, CourseRole.STUDENT);
        Post task = buildTaskPost(course);
        Solution solution = buildSolution(task);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(student));
        when(postRepository.findById(postId)).thenReturn(Optional.of(task));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));

        SolutionDto result = solutionService.getSolution(courseId, postId, solutionId, studentId);

        assertThat(result.getId()).isEqualTo(solutionId);
    }

    @Test
    void getSolution_throwsForbiddenForNonOwningStudent() {
        UUID otherStudentId = UUID.randomUUID();
        Course course = buildCourse();
        CourseMember other = buildMember(course, otherStudentId, CourseRole.STUDENT);
        Post task = buildTaskPost(course);
        Solution solution = buildSolution(task);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, otherStudentId))
                .thenReturn(Optional.of(other));
        when(postRepository.findById(postId)).thenReturn(Optional.of(task));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));

        assertThatThrownBy(() -> solutionService.getSolution(courseId, postId, solutionId, otherStudentId))
                .isInstanceOf(ForbiddenException.class);
    }
    @Test
    void updateSolution_updatesOwnSolution() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, studentId, CourseRole.STUDENT);
        Post task = buildTaskPost(course);
        Solution solution = buildSolution(task);
        CreateSolutionRequest request = new CreateSolutionRequest();
        request.setText("Updated answer");

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(student));
        when(postRepository.findById(postId)).thenReturn(Optional.of(task));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(solutionRepository.save(any(Solution.class))).thenAnswer(inv -> inv.getArgument(0));

        SolutionDto result = solutionService.updateSolution(courseId, postId, solutionId, request, studentId);

        assertThat(result.getText()).isEqualTo("Updated answer");
    }

    @Test
    void updateSolution_throwsForbiddenForNonOwner() {
        UUID otherStudentId = UUID.randomUUID();
        Course course = buildCourse();
        CourseMember other = buildMember(course, otherStudentId, CourseRole.STUDENT);
        Post task = buildTaskPost(course);
        Solution solution = buildSolution(task);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, otherStudentId))
                .thenReturn(Optional.of(other));
        when(postRepository.findById(postId)).thenReturn(Optional.of(task));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));

        CreateSolutionRequest request = new CreateSolutionRequest();
        request.setText("Hacked");

        assertThatThrownBy(() -> solutionService.updateSolution(courseId, postId, solutionId, request, otherStudentId))
                .isInstanceOf(ForbiddenException.class);
    }
    @Test
    void deleteSolution_deletesOwnSolution() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, studentId, CourseRole.STUDENT);
        Post task = buildTaskPost(course);
        Solution solution = buildSolution(task);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(student));
        when(postRepository.findById(postId)).thenReturn(Optional.of(task));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));

        solutionService.deleteSolution(courseId, postId, solutionId, studentId);

        verify(solutionRepository).delete(solution);
    }

    @Test
    void deleteSolution_throwsForbiddenForNonOwner() {
        UUID otherStudentId = UUID.randomUUID();
        Course course = buildCourse();
        CourseMember other = buildMember(course, otherStudentId, CourseRole.STUDENT);
        Post task = buildTaskPost(course);
        Solution solution = buildSolution(task);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, otherStudentId))
                .thenReturn(Optional.of(other));
        when(postRepository.findById(postId)).thenReturn(Optional.of(task));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));

        assertThatThrownBy(() -> solutionService.deleteSolution(courseId, postId, solutionId, otherStudentId))
                .isInstanceOf(ForbiddenException.class);
    }
    @Test
    void gradeSolution_setsGradeWhenTeacher() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, teacherId, CourseRole.TEACHER);
        Post task = buildTaskPost(course);
        Solution solution = buildSolution(task);
        GradeRequest request = new GradeRequest();
        request.setGrade(85);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, teacherId))
                .thenReturn(Optional.of(teacher));
        when(postRepository.findById(postId)).thenReturn(Optional.of(task));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(solutionRepository.save(any(Solution.class))).thenAnswer(inv -> inv.getArgument(0));

        SolutionDto result = solutionService.gradeSolution(courseId, postId, solutionId, request, teacherId);

        assertThat(result.getGrade()).isEqualTo(85);
        assertThat(result.getStatus()).isEqualTo(SolutionStatus.GRADED);
    }

    @Test
    void gradeSolution_throwsForbiddenWhenStudent() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, studentId, CourseRole.STUDENT);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(student));

        GradeRequest request = new GradeRequest();
        request.setGrade(100);

        assertThatThrownBy(() -> solutionService.gradeSolution(courseId, postId, solutionId, request, studentId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void gradeSolution_throwsNotFoundWhenSolutionDoesNotExist() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, teacherId, CourseRole.TEACHER);
        Post task = buildTaskPost(course);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, teacherId))
                .thenReturn(Optional.of(teacher));
        when(postRepository.findById(postId)).thenReturn(Optional.of(task));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.empty());

        GradeRequest request = new GradeRequest();
        request.setGrade(50);

        assertThatThrownBy(() -> solutionService.gradeSolution(courseId, postId, solutionId, request, teacherId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removeGrade_clearsGradeWhenTeacher() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, teacherId, CourseRole.TEACHER);
        Post task = buildTaskPost(course);
        Solution solution = buildSolution(task);
        solution.setGrade(85);
        solution.setStatus(SolutionStatus.GRADED);
        solution.setGradedAt(Instant.now());

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, teacherId))
                .thenReturn(Optional.of(teacher));
        when(postRepository.findById(postId)).thenReturn(Optional.of(task));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(solutionRepository.save(any(Solution.class))).thenAnswer(inv -> inv.getArgument(0));

        SolutionDto result = solutionService.removeGrade(courseId, postId, solutionId, teacherId);

        assertThat(result.getGrade()).isNull();
        assertThat(result.getStatus()).isEqualTo(SolutionStatus.SUBMITTED);
        assertThat(result.getGradedAt()).isNull();
    }

    @Test
    void removeGrade_throwsForbiddenWhenStudent() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, studentId, CourseRole.STUDENT);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(student));

        assertThatThrownBy(() -> solutionService.removeGrade(courseId, postId, solutionId, studentId))
                .isInstanceOf(ForbiddenException.class);
    }
}

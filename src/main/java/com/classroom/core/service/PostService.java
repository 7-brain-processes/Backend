package com.classroom.core.service;

import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.dto.post.CreatePostRequest;
import com.classroom.core.dto.post.PostDto;
import com.classroom.core.dto.post.UpdatePostRequest;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.Course;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.Post;
import com.classroom.core.model.PostType;
import com.classroom.core.model.TeamFormationMode;
import com.classroom.core.model.User;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;

    public Page<PostDto> listPosts(UUID courseId, PostType type, Pageable pageable, UUID userId) {
        getCourseOrThrow(courseId);
        ensureMember(courseId, userId);

        Page<Post> posts = (type == null)
                ? postRepository.findByCourseId(courseId, pageable)
                : postRepository.findByCourseIdAndType(courseId, type, pageable);

        return posts.map(this::toDto);
    }

    @Transactional
    public PostDto createPost(UUID courseId, CreatePostRequest request, UUID userId) {
        Course course = getCourseOrThrow(courseId);
        ensureTeacher(courseId, userId);

        Post post = Post.builder()
                .course(course)
                .author(User.builder().id(userId).build())
                .title(request.getTitle())
                .content(request.getContent())
                .type(request.getType())
                .teamFormationMode(resolveInitialTeamFormationMode(request))
                .deadline(request.getDeadline())
                .build();

        Post saved = postRepository.save(post);
        return toDto(saved);
    }

    public PostDto getPost(UUID courseId, UUID postId, UUID userId) {
        getCourseOrThrow(courseId);
        ensureMember(courseId, userId);

        Post post = getPostInCourseOrThrow(courseId, postId);

        return toDto(post);
    }

    @Transactional
    public PostDto updatePost(UUID courseId, UUID postId, UpdatePostRequest request, UUID userId) {
        getCourseOrThrow(courseId);
        ensureTeacher(courseId, userId);

        Post post = getPostInCourseOrThrow(courseId, postId);

        if (request.getTitle() != null) {
            post.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            post.setContent(request.getContent());
        }
        if (request.getDeadline() != null) {
            post.setDeadline(request.getDeadline());
        }
        if (request.getTeamFormationMode() != null) {
            if (post.getType() != PostType.TASK) {
                throw new BadRequestException("Team formation mode is only available for task posts");
            }
            post.setTeamFormationMode(request.getTeamFormationMode());
        }

        Post saved = postRepository.save(post);
        return toDto(saved);
    }

    public TeamFormationMode getTeamFormationMode(UUID courseId, UUID postId, UUID userId) {
        getCourseOrThrow(courseId);
        ensureMember(courseId, userId);

        Post post = getPostInCourseOrThrow(courseId, postId);
        if (post.getType() != PostType.TASK) {
            throw new BadRequestException("Team formation mode is only available for task posts");
        }

        return resolveTeamFormationMode(post);
    }

    @Transactional
    public TeamFormationMode setTeamFormationMode(UUID courseId, UUID postId, TeamFormationMode mode, UUID userId) {
        if (mode == null) {
            throw new BadRequestException("Team formation mode is required");
        }

        getCourseOrThrow(courseId);
        ensureTeacher(courseId, userId);

        Post post = getPostInCourseOrThrow(courseId, postId);
        if (post.getType() != PostType.TASK) {
            throw new BadRequestException("Team formation mode is only available for task posts");
        }

        post.setTeamFormationMode(mode);
        postRepository.save(post);
        return mode;
    }

    @Transactional
    public void deletePost(UUID courseId, UUID postId, UUID userId) {
        getCourseOrThrow(courseId);
        ensureTeacher(courseId, userId);

        Post post = getPostInCourseOrThrow(courseId, postId);

        postRepository.delete(post);
        postRepository.flush();
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
        CourseMember member = courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));

        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can manage posts");
        }

        return member;
    }

    private PostDto toDto(Post post) {
        return PostDto.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .type(post.getType())
                .teamFormationMode(resolveTeamFormationMode(post))
                .deadline(post.getDeadline())
                .author(post.getAuthor() == null ? null : UserDto.from(post.getAuthor()))
                .materialsCount(post.getFiles() == null ? 0 : post.getFiles().size())
                .commentsCount(post.getComments() == null ? 0 : post.getComments().size())
                .solutionsCount(null)
                .mySolutionId(null)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private Post getPostInCourseOrThrow(UUID courseId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!post.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Post not found");
        }

        return post;
    }

    private TeamFormationMode resolveInitialTeamFormationMode(CreatePostRequest request) {
        if (request.getType() != PostType.TASK) {
            if (request.getTeamFormationMode() != null) {
                throw new BadRequestException("Team formation mode is only available for task posts");
            }
            return null;
        }

        return request.getTeamFormationMode() == null
                ? TeamFormationMode.FREE
                : request.getTeamFormationMode();
    }

    private TeamFormationMode resolveTeamFormationMode(Post post) {
        if (post.getType() != PostType.TASK) {
            return null;
        }

        return post.getTeamFormationMode() == null
                ? TeamFormationMode.FREE
                : post.getTeamFormationMode();
    }
}

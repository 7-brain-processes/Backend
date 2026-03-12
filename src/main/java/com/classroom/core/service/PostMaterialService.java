package com.classroom.core.service;

import com.classroom.core.dto.file.FileDto;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.PostFile;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.PostFileRepository;
import com.classroom.core.repository.PostRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostMaterialService {

    private final PostFileRepository postFileRepository;
    private final PostRepository postRepository;
    private final CourseMemberRepository courseMemberRepository;

    @Value("${app.storage.path}")
    private String storagePath;

    public List<FileDto> listPostMaterials(UUID courseId, UUID postId, UUID userId) {
        requireMember(courseId, userId);
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        return postFileRepository.findByPostId(postId).stream()
                .map(FileDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public FileDto uploadPostMaterial(UUID courseId, UUID postId, MultipartFile file, UUID userId) {
        CourseMember member = requireMember(courseId, userId);
        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can upload materials");
        }
        var post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path target = Path.of(storagePath).resolve(fileName);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
        PostFile postFile = PostFile.builder()
                .post(post)
                .originalName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .storagePath(target.toString())
                .build();
        return FileDto.from(postFileRepository.save(postFile));
    }

    @Transactional
    public void deletePostMaterial(UUID courseId, UUID postId, UUID fileId, UUID userId) {
        CourseMember member = requireMember(courseId, userId);
        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can delete materials");
        }
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        PostFile file = postFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        postFileRepository.delete(file);
    }

    public Resource downloadPostMaterial(UUID courseId, UUID postId, UUID fileId, UUID userId) {
        requireMember(courseId, userId);
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        PostFile file = postFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        try {
            Resource resource = new UrlResource(Path.of(file.getStoragePath()).toUri());
            if (!resource.exists()) {
                throw new ResourceNotFoundException("File not found on disk");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("File not accessible");
        }
    }

    private CourseMember requireMember(UUID courseId, UUID userId) {
        return courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this course"));
    }
}

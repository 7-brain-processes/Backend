package com.classroom.core.service;

import com.classroom.core.dto.file.FileDto;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.PostFileRepository;
import com.classroom.core.repository.PostRepository;
import org.springframework.core.io.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostMaterialService {

    private final PostFileRepository postFileRepository;
    private final PostRepository postRepository;
    private final CourseMemberRepository courseMemberRepository;

    public List<FileDto> listPostMaterials(UUID courseId, UUID postId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public FileDto uploadPostMaterial(UUID courseId, UUID postId, MultipartFile file, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void deletePostMaterial(UUID courseId, UUID postId, UUID fileId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Resource downloadPostMaterial(UUID courseId, UUID postId, UUID fileId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}

package com.classroom.core.service;

import com.classroom.core.dto.file.FileDto;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.SolutionFileRepository;
import com.classroom.core.repository.SolutionRepository;
import org.springframework.core.io.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SolutionFileService {

    private final SolutionFileRepository solutionFileRepository;
    private final SolutionRepository solutionRepository;
    private final CourseMemberRepository courseMemberRepository;

    public List<FileDto> listSolutionFiles(UUID courseId, UUID postId, UUID solutionId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public FileDto uploadSolutionFile(UUID courseId, UUID postId, UUID solutionId,
                                      MultipartFile file, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void deleteSolutionFile(UUID courseId, UUID postId, UUID solutionId,
                                   UUID fileId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Resource downloadSolutionFile(UUID courseId, UUID postId, UUID solutionId,
                                         UUID fileId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}

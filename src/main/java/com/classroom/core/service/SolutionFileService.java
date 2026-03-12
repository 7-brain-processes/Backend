package com.classroom.core.service;

import com.classroom.core.dto.file.FileDto;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.SolutionFile;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.SolutionFileRepository;
import com.classroom.core.repository.SolutionRepository;
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
public class SolutionFileService {

    private final SolutionFileRepository solutionFileRepository;
    private final SolutionRepository solutionRepository;
    private final CourseMemberRepository courseMemberRepository;

    @Value("${app.storage.path}")
    private String storagePath;

    public List<FileDto> listSolutionFiles(UUID courseId, UUID postId, UUID solutionId, UUID userId) {
        requireMember(courseId, userId);
        solutionRepository.findById(solutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found"));
        return solutionFileRepository.findBySolutionId(solutionId).stream()
                .map(FileDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public FileDto uploadSolutionFile(UUID courseId, UUID postId, UUID solutionId,
                                      MultipartFile file, UUID userId) {
        requireMember(courseId, userId);
        var solution = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found"));
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path target = Path.of(storagePath).resolve(fileName);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
        SolutionFile solutionFile = SolutionFile.builder()
                .solution(solution)
                .originalName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .storagePath(target.toString())
                .build();
        return FileDto.from(solutionFileRepository.save(solutionFile));
    }

    @Transactional
    public void deleteSolutionFile(UUID courseId, UUID postId, UUID solutionId,
                                   UUID fileId, UUID userId) {
        requireMember(courseId, userId);
        solutionRepository.findById(solutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found"));
        SolutionFile file = solutionFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        solutionFileRepository.delete(file);
    }

    public Resource downloadSolutionFile(UUID courseId, UUID postId, UUID solutionId,
                                         UUID fileId, UUID userId) {
        requireMember(courseId, userId);
        solutionRepository.findById(solutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found"));
        SolutionFile file = solutionFileRepository.findById(fileId)
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

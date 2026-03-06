package com.classroom.core.service;

import com.classroom.core.dto.solution.CreateSolutionRequest;
import com.classroom.core.dto.solution.GradeRequest;
import com.classroom.core.dto.solution.SolutionDto;
import com.classroom.core.model.SolutionStatus;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.PostRepository;
import com.classroom.core.repository.SolutionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SolutionService {

    private final SolutionRepository solutionRepository;
    private final PostRepository postRepository;
    private final CourseMemberRepository courseMemberRepository;

    public Page<SolutionDto> listSolutions(UUID courseId, UUID postId, SolutionStatus status,
                                           Pageable pageable, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public SolutionDto createSolution(UUID courseId, UUID postId, CreateSolutionRequest request, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public SolutionDto getMySolution(UUID courseId, UUID postId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public SolutionDto getSolution(UUID courseId, UUID postId, UUID solutionId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public SolutionDto updateSolution(UUID courseId, UUID postId, UUID solutionId,
                                      CreateSolutionRequest request, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void deleteSolution(UUID courseId, UUID postId, UUID solutionId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public SolutionDto gradeSolution(UUID courseId, UUID postId, UUID solutionId,
                                     GradeRequest request, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}

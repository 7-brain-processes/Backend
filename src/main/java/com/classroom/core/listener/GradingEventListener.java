package com.classroom.core.listener;

import com.classroom.core.event.SolutionGradedEvent;
import com.classroom.core.model.Solution;
import com.classroom.core.model.SolutionStatus;
import com.classroom.core.repository.SolutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GradingEventListener {

    private final SolutionRepository solutionRepository;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSolutionGraded(SolutionGradedEvent event) {
        Solution solution = solutionRepository.findById(event.getSolutionId()).orElse(null);
        if (solution == null) {
            return;
        }
        solution.setGrade(event.getGrade());
        solution.setStatus(SolutionStatus.GRADED);
        solution.setGradedAt(event.getGradedAt());
        solutionRepository.save(solution);
    }
}

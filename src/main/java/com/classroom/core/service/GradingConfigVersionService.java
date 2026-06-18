package com.classroom.core.service;

import com.classroom.core.model.Criterion;
import com.classroom.core.model.GradingConfig;
import com.classroom.core.model.GradingConfigVersion;
import com.classroom.core.repository.CriterionRepository;
import com.classroom.core.repository.GradingConfigVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Handles creation and reuse of immutable {@link GradingConfigVersion} snapshots.
 * Centralises the version-matching logic so that peer-review grade application
 * can create assessment results using the same snapshot that manual grading uses.
 */
@Service
@RequiredArgsConstructor
public class GradingConfigVersionService {

    private final CriterionRepository criterionRepository;
    private final GradingConfigVersionRepository gradingConfigVersionRepository;

    public GradingConfigVersion findOrCreateVersion(GradingConfig config) {
        List<Criterion> currentCriteria = criterionRepository
                .findByGradingConfigIdOrderBySortOrderAsc(config.getId());

        Optional<GradingConfigVersion> latestOpt = gradingConfigVersionRepository
                .findTopByPostIdOrderByVersionNumberDesc(config.getPost().getId());

        if (latestOpt.isPresent()) {
            GradingConfigVersion latest = latestOpt.get();
            if (config.matchesVersion(latest, currentCriteria)) {
                return latest;
            }
            GradingConfigVersion next = config.snapshotVersion(latest.getVersionNumber() + 1, currentCriteria);
            return gradingConfigVersionRepository.save(next);
        }

        GradingConfigVersion first = config.snapshotVersion(1, currentCriteria);
        return gradingConfigVersionRepository.save(first);
    }
}

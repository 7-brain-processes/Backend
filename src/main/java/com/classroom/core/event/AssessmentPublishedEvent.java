package com.classroom.core.event;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class AssessmentPublishedEvent implements DomainEvent {

    private final UUID assessmentResultId;
    private final UUID solutionId;
    private final Instant occurredOn;

    public AssessmentPublishedEvent(UUID assessmentResultId, UUID solutionId, Instant occurredOn) {
        this.assessmentResultId = assessmentResultId;
        this.solutionId = solutionId;
        this.occurredOn = occurredOn;
    }

    @Override
    public Instant occurredOn() {
        return occurredOn;
    }
}

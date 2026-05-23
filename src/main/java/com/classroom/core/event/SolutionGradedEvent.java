package com.classroom.core.event;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class SolutionGradedEvent implements DomainEvent {

    private final UUID solutionId;
    private final Integer grade;
    private final Instant gradedAt;
    private final Instant occurredOn;

    public SolutionGradedEvent(UUID solutionId, Integer grade, Instant gradedAt, Instant occurredOn) {
        this.solutionId = solutionId;
        this.grade = grade;
        this.gradedAt = gradedAt;
        this.occurredOn = occurredOn;
    }

    @Override
    public Instant occurredOn() {
        return occurredOn;
    }
}

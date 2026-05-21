package com.classroom.core.event;

import java.time.Instant;

public interface DomainEvent {
    Instant occurredOn();
}

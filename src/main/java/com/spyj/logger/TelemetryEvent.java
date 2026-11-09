package com.spyj.logger;

import java.time.Instant;
import java.util.Map;

public class TelemetryEvent {

    private final Instant timestamp;
    private final EventType eventType;
    private final Map<String, Object> eventDetails;

    public TelemetryEvent (EventType type, Map<String, Object> details) {
        this.timestamp = Instant.now();
        this.eventType = type;
        this.eventDetails = details;
    }

    public TelemetryEvent (EventType type, Map<String, Object> details, Instant time) {
        this.eventType = type;
        this.eventDetails = details;
        this.timestamp = time;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public EventType getEventType() {
        return eventType;
    }

    public Map<String, Object> getEventDetails() {
        return eventDetails;
    }
}

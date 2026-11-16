package com.spyj.logger;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a single captured BlueJ event with a UTC timestamp and structured metadata.
 * Created by {@link SpyJLogger#recordEvent} and pushed to all registered {@link EventSubscriber}s.
 * The event details map uses {@link DetailsKey} label strings as keys.
 *
 * @author Mykel Dedier
 */
public class TelemetryEvent {

    /** The UTC instant at which this event was recorded. */
    private final Instant timestamp;

    /** The category of BlueJ event that this record represents. */
    private final EventType eventType;

    /** The extracted metadata from the captured BlueJ event, keyed by {@link DetailsKey} label strings. */
    private final Map<String, Object> eventDetails;

    /**
     * Creates a telemetry event with the current UTC time as its timestamp.
     *
     * @param type    the category of event being recorded
     * @param details the metadata extracted from the BlueJ event
     */
    public TelemetryEvent(EventType type, Map<String, Object> details) {
        this.timestamp = Instant.now();
        this.eventType = type;
        this.eventDetails = details;
    }

    /**
     * Creates a telemetry event with an explicitly supplied timestamp.
     * Useful when replaying events or supplying a custom capture time.
     *
     * @param type    the category of event being recorded
     * @param details the metadata extracted from the BlueJ event
     * @param time    the UTC instant to use as the event timestamp
     */
    public TelemetryEvent(EventType type, Map<String, Object> details, Instant time) {
        this.eventType = type;
        this.eventDetails = details;
        this.timestamp = time;
    }

    /**
     * Returns the UTC instant at which this event was recorded.
     *
     * @return the event timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the category of BlueJ event this record represents.
     *
     * @return the event type
     */
    public EventType getEventType() {
        return eventType;
    }

    /**
     * Returns the metadata map extracted from the captured BlueJ event.
     * Keys are {@link DetailsKey} label strings; values vary by event type.
     *
     * @return the event detail map
     */
    public Map<String, Object> getEventDetails() {
        return eventDetails;
    }
}

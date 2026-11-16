package com.spyj.logger;

import java.util.ArrayList;
import java.util.List;

/**
 * An append-only, thread-safe log of every {@link TelemetryEvent} received during a session.
 * Implements {@link EventSubscriber} to receive events directly from {@link SpyJLogger}.
 * Acts as the primary data source for the report exporter at session end.
 *
 * <p>All public methods are {@code synchronized} to allow safe concurrent access from
 * both the BlueJ event thread and the JavaFX Application Thread used by EditorListener.</p>
 *
 * @author Mykel Dedier
 */
public class EventStore implements EventSubscriber {

    /** The ordered list of all telemetry events captured during the session. */
    private List<TelemetryEvent> events;

    /**
     * Creates a new EventStore with an empty event list.
     */
    public EventStore() {
        this.events = new ArrayList<>();
    }

    /**
     * Appends a non-null event to the store.
     * Null events are silently ignored.
     *
     * @param event the telemetry event to store
     */
    @Override
    public synchronized void onEvent(TelemetryEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    /**
     * Returns an unmodifiable snapshot of all events recorded so far.
     *
     * @return an unmodifiable list of all telemetry events
     */
    public synchronized List<TelemetryEvent> getEvents() {
        return List.copyOf(events);
    }

    /**
     * Returns an unmodifiable list of events matching the specified type.
     *
     * @param type the event type to filter by
     * @return an unmodifiable list of events with the given type
     */
    public synchronized List<TelemetryEvent> getEvents(EventType type) {
        List<TelemetryEvent> matches = new ArrayList<>();
        for (TelemetryEvent event : events) {
            if (event.getEventType() == type) {
                matches.add(event);
            }
        }
        return matches;
    }

    /**
     * Returns the total number of events stored.
     *
     * @return the event count
     */
    public synchronized int size() {
        return events.size();
    }

    /**
     * Removes all events from the store.
     */
    public synchronized void clear() {
        events.clear();
    }
}

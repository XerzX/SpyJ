package com.spyj.logger;

import java.util.ArrayList;
import java.util.Map;

/**
 * Central event bus for the SpyJ telemetry pipeline.
 * All five listener classes hold a reference to the same {@code SpyJLogger} instance
 * and call {@link #recordEvent} to publish captured BlueJ event data.
 * Follows the Observer pattern as the Subject role; registered {@link EventSubscriber}s
 * are notified for every recorded event.
 *
 * <p>Individual subscriber failures are caught and printed to stdout so that one
 * failing subscriber cannot disrupt the others or the running BlueJ process.</p>
 *
 * @author Mykel Dedier
 */
public class SpyJLogger {

    /** The list of subscribers notified whenever an event is recorded. */
    private ArrayList<EventSubscriber> subscribers;

    /**
     * Creates a new SpyJLogger with an empty subscriber list.
     */
    public SpyJLogger() {
        this.subscribers = new ArrayList<>();
    }

    /**
     * Registers a new subscriber to receive telemetry events.
     * Called during {@code SpyJ.startup()} to attach {@code EventStore} and {@code StatsTracker}.
     *
     * @param eventSubscriber the subscriber to add
     */
    public void addSubscribers(EventSubscriber eventSubscriber) {
        subscribers.add(eventSubscriber);
    }

    /**
     * Wraps the supplied event data into a timestamped {@link TelemetryEvent} and
     * pushes it to every registered subscriber.
     * Each push is wrapped in a try/catch; failures are printed to stdout, which BlueJ
     * redirects to its log file.
     *
     * @param eventType    the category of the captured BlueJ event
     * @param eventDetails the metadata extracted from the BlueJ event
     */
    public void recordEvent(EventType eventType, Map<String, Object> eventDetails) {
        TelemetryEvent event = new TelemetryEvent(eventType, eventDetails);
        for (EventSubscriber eventSubscriber : subscribers) {
            try {
                eventSubscriber.onEvent(event);
            } catch (Exception exception) {
                System.out.println("Failed To Log Event: " + event.getEventType() + " | " + eventDetails);
            }
        }
    }
}

package com.spyj.logger;

import java.util.ArrayList;
import java.util.List;

public class EventStore implements EventSubscriber {

    private List<TelemetryEvent> events;

    public EventStore () {
        this.events = new ArrayList<>();
    }

    public synchronized void onEvent (TelemetryEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    public synchronized List<TelemetryEvent> getEvents () {
        return List.copyOf(events);
    }

    public synchronized List<TelemetryEvent> getEvents (EventType type) {
        List<TelemetryEvent> matches = new ArrayList<>();
        for (TelemetryEvent event : events) {
            if (event.getEventType() == type) {
                matches.add(event);
            }
        }
        return matches;
    }

    public synchronized int size () {
        return events.size();
    }

    public synchronized void clear () {
        events.clear();
    }
}

package com.spyj.logger;

import java.util.ArrayList;
import java.util.Map;

public class SpyJLogger {

    private ArrayList<EventSubscriber> subscribers;

    public SpyJLogger () {
        this.subscribers = new ArrayList<>();
    }

    public void addSubscribers (EventSubscriber eventSubscriber) {
        subscribers.add(eventSubscriber);
    }

    public void recordEvent (EventType eventType, Map<String, Object> eventDetails) {
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

package com.spyj.logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EventStoreTest {
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        eventStore = new EventStore();
    }

    @Test
    void testGetEventsByType() {
        eventStore.onEvent(new TelemetryEvent(EventType.PROJECT_OPENED, new HashMap<>()));
        eventStore.onEvent(new TelemetryEvent(EventType.CLASS_CREATED, new HashMap<>()));
        eventStore.onEvent(new TelemetryEvent(EventType.CLASS_CREATED, new HashMap<>()));

        List<TelemetryEvent> classCreatedEvents = eventStore.getEvents(EventType.CLASS_CREATED);
        assertEquals(2, classCreatedEvents.size());
        assertEquals(EventType.CLASS_CREATED, classCreatedEvents.get(0).getEventType());
    }

    @Test
    void testClear() {
        eventStore.onEvent(new TelemetryEvent(EventType.PROJECT_OPENED, new HashMap<>()));
        eventStore.clear();
        assertEquals(0, eventStore.size());
    }
}
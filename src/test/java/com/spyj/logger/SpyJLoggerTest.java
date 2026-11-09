package com.spyj.logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SpyJLoggerTest {
    private SpyJLogger logger;
    private EventSubscriber fakesubscriber;

    @BeforeEach
    void setup() {
        logger = new SpyJLogger();
        fakesubscriber = mock(EventSubscriber.class);
    }

    @Test
    void testAddSubscriberAndRecordEvent() {
        logger.addSubscribers(fakesubscriber);
        Map<String, Object> details = new HashMap<>();
        details.put("Key" , "Value");
        
        logger.recordEvent(EventType.PROJECT_OPENED, details);

        verify(fakesubscriber, times(1)).onEvent(any(TelemetryEvent.class));
    }

    @Test
    void testSubscriberExceptionDoesNotAffectLogger(){
        EventSubscriber badSubscriber = mock(EventSubscriber.class);
        doThrow(new RuntimeException("Test Exception")).when(badSubscriber).onEvent(any());

        logger.addSubscribers(badSubscriber);
        logger.addSubscribers(fakesubscriber);

        assertDoesNotThrow(() -> logger.recordEvent(EventType.PROJECT_OPENED, new HashMap<>()));
        verify(fakesubscriber, times(1)).onEvent(any(TelemetryEvent.class));
    }
}
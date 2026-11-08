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

    void setup() {
        logger = new SpyJlogger();
        fakesubscriber = mock(EventSubscriber.class);
    }

    void testAddSubscriberAndRecordEvent() {
        logger.addSubscribers(fakesubscriber);
        Map<String, Object> details = new HashMap<>();
        details.put("Key" , "Value");
        
        logger.recordEvent(EventType.PROJECT_OPENED, details);

        verify(fakesubscriber, times(1)).onEvent(any(TelemetryEvent.class));
    }

    void testSubscriberExceptionDoesNotAffectLogger(){
        EventSubscriber badSubscriber = mock(EventSubscriber.class);
        doThrow(new RuntimeEception("Test Exception")).when(badSubscriber).onEvent(any());

        logger.addSubscribers(badSubscriber);
    }
}

package com.spyj.listener;

import com.spyj.logger.EventType;
import com.spyj.logger.SpyJLogger;
import bluej.extensions2.BPackage;
import bluej.extensions2.event.InvocationFinishedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

public class ExecutionListenerTest {
    private ExecutionListener listener;
    private SpyJLogger fakelogger;
    private InvocationFinishedEvent fakeEvent;

    @BeforeEach
    void setup() throws Exception {
        fakelogger = mock(SpyJLogger.class);
        listener = new ExecutionListener(fakelogger);
        fakeEvent = mock(InvocationFinishedEvent.class);

        BPackage fakeBPackage = mock(BPackage.class);
        when(fakeEvent.getPackage()).thenReturn(fakeBPackage);
        when(fakeBPackage.getName()).thenReturn("TestPackage");
    }

    @Test
    void testNormalExecution() {
        when(fakeEvent.getEventType()).thenReturn(InvocationFinishedEvent.EventType.NORMAL_EXIT);
        listener.invocationFinished(fakeEvent);
        verify(fakelogger, times(1)).recordEvent(eq(EventType.EXECUTION_SUCCEEDED), anyMap());
    }

    @Test
    void testAbnormalExecution() {
        when(fakeEvent.getEventType()).thenReturn(InvocationFinishedEvent.EventType.EXCEPTION_EXIT);
        listener.invocationFinished(fakeEvent);
        verify(fakelogger, times(1)).recordEvent(eq(EventType.EXECUTION_EXCEPTION), anyMap());
    }
}
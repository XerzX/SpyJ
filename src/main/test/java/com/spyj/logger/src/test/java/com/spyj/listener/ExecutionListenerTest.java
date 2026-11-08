package com.spyj.listener;

import com.spyj.logger.EventType;
import com.spyj.logger.SpyJLogger;
import bluej.extensions2.BPackage;
import bluej.extensions2.event.ExecutionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

public class ExecutionListenerTest {
    private ExecutionListener listener;
    private SpyJLogger fakelogger;
    private ExecutionEvent fakeEvent;

    void setup() {
        fakelogger = mock(SpyJLogger.class);
        listener = new ExecutionListener(fakelogger);
        fakeEvent = mock(ExecutionEvent.class);

        BPackage fakeBPackage = mock(BPackage.class);
        when(fakeEvent.getBPackage()).thenReturn(fakeBPackage);
        when(fakeBPackage.getName()).thenReturn("TestPackage");
    }

    void testNormalExecution() {
        when(fakeEvent.getEventType()).thenReturn(ExecutionEvent.EventType.NORMAL_EXIT);
        listener.executionFinished(fakeEvent);
        verify(fakeLogger, times(1)).recordEvent(eq(EventType.EXECUTION_SUCCEEDED), anyMap());
    }

    void testAbnormalExecution() {
        when(fakeEvent.getEventType()).thenReturn(ExecutionEvent.EventType.ABNORMAL_EXIT);
        listener.executionFinished(fakeEvent);
        verify(fakeLogger, times(1)).recordEvent(eq(EventType.EXECUTION_FAILED), anyMap());
    }
}

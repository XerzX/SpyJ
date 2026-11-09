package com.spyj.listener;

import com.spyj.logger.EventType;
import com.spyj.logger.SpyJLogger;
import bluej.extensions2.BClass;
import bluej.extensions2.BPackage;
import bluej.extensions2.event.ClassEvent;
import bluej.extensions2.event.ClassListener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.mockito.Mockito.*;

public class ClassListenerTest {
    private com.spyj.listener.ClassListener listener;
    private SpyJLogger fakelogger;
    private ClassEvent fakeEvent;
    private BClass fakeBClass;
    private BPackage fakeBPackage;
    private File fakeFile;

    @BeforeEach
    void setup() throws Exception {
        fakelogger = mock(SpyJLogger.class);
        listener = new com.spyj.listener.ClassListener(fakelogger);
        fakeEvent = mock(ClassEvent.class);
        fakeBClass = mock(BClass.class);
        fakeBPackage = mock(BPackage.class);
        fakeFile = new File("TestClass.java");

        when(fakeEvent.getBClass()).thenReturn(fakeBClass);
        when(fakeBClass.getPackage()).thenReturn(fakeBPackage);
        when(fakeBClass.getJavaFile()).thenReturn(fakeFile);
        when(fakeBClass.getName()).thenReturn("TestClass");
        when(fakeBPackage.getName()).thenReturn("TestPackage");
    }

    @Test
    void testClassCreatedthenModified() {
       listener.classStateChanged(fakeEvent);
       verify(fakelogger, times(1)).recordEvent(eq(EventType.CLASS_CREATED), anyMap());

       listener.classStateChanged(fakeEvent);
       verify(fakelogger, times(1)).recordEvent(eq(EventType.CLASS_MODIFIED), anyMap());
    }

    @Test
    void testClassRemoved() {
        listener.classRemoved(fakeEvent);
        verify(fakelogger, times(1)).recordEvent(eq(EventType.CLASS_DELETED), anyMap());
    }
}
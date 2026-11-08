package com.spyj.listener;

import com.spyj.logger.EventType;
import com.spyj.logger.SpyJLogger;
import bluej.extensions2.event.CompileEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.mockito.Mockito.*;

public class CompilationListenerTest {
   private CompilationListener listener;
   private SpyJLogger fakelogger;
   private CompileEvent fakeEvent; 

   void setup() {
       fakelogger = mock(SpyJLogger.class);
       listener = new CompilationListener(fakelogger);
       fakeEvent = mock(CompileEvent.class);
   }

   void testCompilationSucceeded() {
    when(fakeEvent.getFiles()).thenReturn(new File[]{new File("TestClass.java")});

    listener.compileStarted(fakeEvent);
    listener.compileSucceeded(fakeEvent);

    verify(fakelogger, times(1)).recordEvent(eq(EventType.COMPILATION_SUCCEEDED), anyMap());
   }

   void testCompilationFailed() {
    when(fakeEvent.getFiles()).thenReturn(new File[]{new File("TestClass.java")});

    listener.compileStarted(fakeEvent);
    listener.compileFailed(fakeEvent);

    verify(fakelogger, times(1)).recordEvent(eq(EventType.COMPILATION_FAILED), anyMap());
   }

   void testCompilationError() {
    when(fakeEvent.getErrorLineNumber()).thenReturn(10);
    when(fakeEvent.getErrorMessage()).thenReturn("Syntax error");

    listener.compileError(fakeEvent);

    verify(fakelogger, times(1)).recordEvent(eq(EventType.COMPILATION_ERROR), anyMap());
   }
}

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

   @BeforeEach
   void setup() {
       fakelogger = mock(SpyJLogger.class);
       listener = new CompilationListener(fakelogger);
       fakeEvent = mock(CompileEvent.class);
   }

   @Test
   void testCompilationSucceeded() {
    when(fakeEvent.getFiles()).thenReturn(new File[]{new File("TestClass.java")});

    listener.compileStarted(fakeEvent);
    listener.compileSucceeded(fakeEvent);

    verify(fakelogger, times(1)).recordEvent(eq(EventType.COMPILE_SUCCEEDED), anyMap());
   }

   @Test
   void testCompilationFailed() {
    when(fakeEvent.getFiles()).thenReturn(new File[]{new File("TestClass.java")});

    listener.compileStarted(fakeEvent);
    listener.compileFailed(fakeEvent);

    verify(fakelogger, times(1)).recordEvent(eq(EventType.COMPILE_FAILED), anyMap());
   }

   @Test
   void testCompilationError() {
    when(fakeEvent.getErrorLineNumber()).thenReturn(10);
    when(fakeEvent.getErrorMessage()).thenReturn("Syntax error");

    listener.compileError(fakeEvent);

    verify(fakelogger, times(1)).recordEvent(eq(EventType.COMPILE_ERROR), anyMap());
   }
}
package com.spyj.listener;

import com.spyj.logger.EventType;
import com.spyj.logger.SpyJLogger;
import bluej.extensions2.BPackage;
import bluej.extensions2.BProject;
import bluej.extensions2.BlueJ;
import bluej.extensions2.event.PackageEvent;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ProjectListenerTest {
    private ProjectListener listener;
    private SpyJLogger fakelogger;
    private BlueJ fakeBlueJ;
    private ClassListener fakeclasslistener;
    private PackageEvent fakeEvent;
    private BPackage fakeBPackage;
    private BProject fakeBProject;
    private File fakeDir;

    @BeforeEach
    void setup() throws Exception {
        fakelogger = mock(SpyJLogger.class);
        fakeBlueJ = mock(BlueJ.class);
        fakeclasslistener = mock(ClassListener.class);
        listener = new ProjectListener(fakeBlueJ, fakelogger, fakeclasslistener);
        fakeEvent = mock(PackageEvent.class);
        fakeBPackage = mock(BPackage.class);
        fakeBProject = mock(BProject.class);
        fakeDir = new File("TestProject");

        when(fakeEvent.getPackage()).thenReturn(fakeBPackage);
        when(fakeBPackage.getProject()).thenReturn(fakeBProject);
        when(fakeBPackage.getName()).thenReturn("TestPackage");
        when(fakeBProject.getDir()).thenReturn(fakeDir);
        when(fakeBProject.getName()).thenReturn("TestProject");
    }

    private void closePackage() {
        try (MockedStatic<Platform> fakeplatform = mockStatic(Platform.class)) {
            fakeplatform.when(() -> Platform.runLater(any(Runnable.class))).thenAnswer(invocation -> {
                ((Runnable) invocation.getArgument(0)).run();
                return null;
            });
            listener.packageClosing(fakeEvent);
        }
    }

    @Test
    void testProjectOpenedRecordedOnce() throws Exception {
        listener.packageOpened(fakeEvent);
        listener.packageOpened(fakeEvent);

        verify(fakelogger, times(1)).recordEvent(eq(EventType.PROJECT_OPENED), anyMap());
        verify(fakeclasslistener, times(2)).addExistingClasses(fakeBPackage);
    }

    @Test
    void testOpenedProjectFailureDoesNotThrow() throws Exception {
        doThrow(new RuntimeException("Test Exception")).when(fakeBPackage).getProject();

        assertDoesNotThrow(() -> listener.packageOpened(fakeEvent));
        verifyNoInteractions(fakelogger);
    }

    @Test
    void testProjectClosedWhenNoProjectsOpen() throws Exception {
        listener.packageOpened(fakeEvent);
        when(fakeBlueJ.getOpenProjects()).thenReturn(new BProject[0]);

        closePackage();

        verify(fakelogger, times(1)).recordEvent(eq(EventType.PROJECT_CLOSED), anyMap());
    }

    @Test
    void testProjectNotClosedWhileStillOpen() throws Exception {
        listener.packageOpened(fakeEvent);
        when(fakeBlueJ.getOpenProjects()).thenReturn(new BProject[]{fakeBProject});

        closePackage();

        verify(fakelogger, never()).recordEvent(eq(EventType.PROJECT_CLOSED), anyMap());
    }

    @Test
    void testProjectReopenedAfterClose() throws Exception {
        listener.packageOpened(fakeEvent);
        when(fakeBlueJ.getOpenProjects()).thenReturn(new BProject[0]);
        closePackage();

        listener.packageOpened(fakeEvent);

        verify(fakelogger, times(2)).recordEvent(eq(EventType.PROJECT_OPENED), anyMap());
    }

    @Test
    void testClosedProjectFailureDoesNotThrow() throws Exception {
        doThrow(new RuntimeException("Test Exception")).when(fakeBPackage).getProject();

        assertDoesNotThrow(() -> listener.packageClosing(fakeEvent));
        verifyNoInteractions(fakelogger);
    }

    @Test
    void testProjectClosedWhenOpenProjectsLookupFails() throws Exception {
        listener.packageOpened(fakeEvent);
        when(fakeBlueJ.getOpenProjects()).thenThrow(new RuntimeException("Test Exception"));

        closePackage();

        verify(fakelogger, times(1)).recordEvent(eq(EventType.PROJECT_CLOSED), anyMap());
    }
}
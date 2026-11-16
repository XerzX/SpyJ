package com.spyj;

import java.io.IOException;
import java.time.Instant;

import com.spyj.format.ReportExporter;
import com.spyj.listener.ClassListener;
import com.spyj.listener.CompilationListener;
import com.spyj.listener.EditorListener;
import com.spyj.listener.ExecutionListener;
import com.spyj.listener.ProjectListener;
import com.spyj.logger.EventStore;
import com.spyj.logger.SpyJLogger;
import com.spyj.logger.StatsTracker;

import bluej.extensions2.BlueJ;
import bluej.extensions2.Extension;

public class SpyJ extends Extension {

    private SpyJLogger sJLogger;
    private EventStore eventStore;
    private StatsTracker statsTracker;
    private ReportExporter reportExporter;
    private Instant sessionStart;
    private boolean reportGenerated;

    private ClassListener classListener;
    private ProjectListener projectListener;
    private CompilationListener compilationListener;
    private ExecutionListener executionListener;
    private EditorListener editorListener;

    public void startup (BlueJ bluej) {
        sJLogger = new SpyJLogger();
        eventStore = new EventStore();
        statsTracker = new StatsTracker();
        reportExporter = new ReportExporter();
        sessionStart = Instant.now();
        reportGenerated = false;

        sJLogger.addSubscribers(eventStore);
        sJLogger.addSubscribers(statsTracker);

        classListener = new ClassListener(sJLogger);
        projectListener = new ProjectListener(bluej, sJLogger, classListener);
        compilationListener = new CompilationListener(sJLogger);
        executionListener = new ExecutionListener(sJLogger);
        editorListener = new EditorListener(sJLogger);

        bluej.addClassListener(classListener);
        bluej.addPackageListener(projectListener);
        bluej.addCompileListener(compilationListener);
        bluej.addInvocationFinishedListener(executionListener);
    }

    public void terminate () {
        // Guard against double-export or a startup that never completed.
        if (reportGenerated || eventStore == null || statsTracker == null) {
            return;
        }

        // Flush any in-progress editor burst before reading the store.
        if (editorListener != null) {
            editorListener.stop();
        }

        // Nothing to report if no events were recorded.
        if (eventStore.getEvents().isEmpty()) {
            reportGenerated = true;
            return;
        }

        String sessionId = "spyj-session-" + sessionStart.toEpochMilli();

        try {
            reportExporter.export(
                eventStore,
                statsTracker,
                sessionId,
                "unknown",
                sessionStart,
                Instant.now()
            );
            reportGenerated = true;
        } catch (IOException e) {
            System.err.println("SpyJ: failed to export telemetry report: " + e.getMessage());
        }
    }

    public boolean isCompatible () {
        return (getExtensionsAPIVersionMajor() >= 3);
    }

    public String getVersion () {
        return "1.0.0";
    }

    public String getName () {
        return "SpyJ";
    }

    public String getDescription () {
        return "A data analytics plugin for collecting, formatting and analysing telemetry data";
    }
}

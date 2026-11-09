package com.spyj;

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

    SpyJLogger sJLogger;
    EventStore eventStore;
    StatsTracker statsTracker;

    private ClassListener classListener;
    private ProjectListener projectListener;
    private CompilationListener compilationListener;
    private ExecutionListener executionListener;
    private EditorListener editorListener;

    public void startup (BlueJ bluej) {
        sJLogger = new SpyJLogger();
        eventStore = new EventStore();
        statsTracker = new StatsTracker();
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

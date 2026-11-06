package com.spyj.listener;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;

import com.spyj.logger.EventType;
import com.spyj.logger.SpyJLogger;

import bluej.extensions2.event.CompileEvent;
import bluej.extensions2.event.CompileListener;

public class CompilationListener implements CompileListener {

    private SpyJLogger sJLogger;
    private Instant startTime;

    public CompilationListener (SpyJLogger sJLogger) {
        this.sJLogger = sJLogger;
    }

    public void compileStarted (CompileEvent e) {
        startTime = Instant.now();
    }

    private void recordCompileLifeCycleEvents (CompileEvent e, EventType eventType, String status) {
        if (startTime != null) {
            long durationInMillis = Duration.between(startTime, Instant.now()).toMillis();
            HashMap<String, Object> compileDetails = new HashMap<>();

            compileDetails.put("Compile Status", status);
            compileDetails.put("Compile Duration", durationInMillis);
            compileDetails.put("User Generated", e.isUserGeneratedCompilation());

            if (e.getFiles() != null) {
                ArrayList<String> compileEventFiles = new ArrayList<>();
                for (File file : e.getFiles()) {
                    compileEventFiles.add(file.getName());
                }
                compileDetails.put("Compiled Files", compileEventFiles);
            }

            sJLogger.recordEvent(eventType, compileDetails);
            startTime = null;
        }
    }

    public void compileSucceeded (CompileEvent e) {
        recordCompileLifeCycleEvents(e, EventType.COMPILE_SUCCEEDED, "Succeeded");
    }

    public void compileFailed (CompileEvent e) {
        recordCompileLifeCycleEvents(e, EventType.COMPILE_FAILED, "Failed");
    }

    public void compileWarning (CompileEvent e) {
        HashMap<String, Object> compileWarningDetails = new HashMap<>();

        compileWarningDetails.put("Warning Start Line", e.getErrorLineNumber());
        compileWarningDetails.put("Warning Message", e.getErrorMessage());
        compileWarningDetails.put("Warning Position", e.getErrorPosition());

        sJLogger.recordEvent(EventType.COMPILE_WARNING, compileWarningDetails);
    }

    public void compileError (CompileEvent e) {
        HashMap<String, Object> compileErrorDetails = new HashMap<>();

        compileErrorDetails.put("Error Start Line", e.getErrorLineNumber());
        compileErrorDetails.put("Error Message", e.getErrorMessage());
        compileErrorDetails.put("Error Position", e.getErrorPosition());
        
        sJLogger.recordEvent(EventType.COMPILE_ERROR, compileErrorDetails);
    }

}

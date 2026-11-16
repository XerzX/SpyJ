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

/**
 * Listens for BlueJ compilation lifecycle events and records them as telemetry events.
 * Implements the BlueJ {@link CompileListener} API.
 *
 * <p>The full lifecycle of a compilation pass is tracked across multiple callbacks:
 * {@link #compileStarted} captures the start time, and
 * {@link #compileSucceeded} / {@link #compileFailed} record the outcome with the
 * elapsed duration. Warnings and per-line errors are recorded independently as they arrive.</p>
 *
 * <p>Recorded event types: {@code COMPILE_SUCCEEDED}, {@code COMPILE_FAILED},
 * {@code COMPILE_WARNING}, {@code COMPILE_ERROR}.</p>
 *
 * @author Mykel Dedier
 */
public class CompilationListener implements CompileListener {

    /** The central logger bus used to publish captured events. */
    private SpyJLogger sjLogger;

    /**
     * The timestamp captured at the start of the most recent compilation attempt.
     * Set to {@code null} after the outcome is recorded to prevent stale measurements.
     */
    private Instant startTime;

    /**
     * Creates a CompilationListener that records compilation events to the given logger.
     *
     * @param sJLogger the central event logger
     */
    public CompilationListener(SpyJLogger sJLogger) {
        this.sjLogger = sJLogger;
    }

    /**
     * Called by BlueJ when a compilation attempt begins.
     * Records the current time so that the compile duration can be calculated
     * when the outcome callback ({@link #compileSucceeded} or {@link #compileFailed}) fires.
     *
     * @param e the BlueJ compile event
     */
    public void compileStarted(CompileEvent e) {
        startTime = Instant.now();
    }

    /**
     * Records a compilation outcome event ({@code COMPILE_SUCCEEDED} or {@code COMPILE_FAILED})
     * with the elapsed duration, user-initiated flag, and compiled file names.
     * Does nothing if {@link #compileStarted} was never called (i.e. {@code startTime} is null).
     * Resets {@code startTime} to {@code null} after recording to prevent stale measurements.
     *
     * @param e         the BlueJ compile event
     * @param eventType the telemetry event type to record
     * @param status    the human-readable status string to include in the event details
     */
    private void recordCompileLifeCycleEvents(CompileEvent e, EventType eventType, String status) {
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

            sjLogger.recordEvent(eventType, compileDetails);
            startTime = null;
        }
    }

    /**
     * Called by BlueJ when a compilation attempt completes without errors.
     * Records a {@code COMPILE_SUCCEEDED} event with duration and file details.
     *
     * @param e the BlueJ compile event
     */
    public void compileSucceeded(CompileEvent e) {
        recordCompileLifeCycleEvents(e, EventType.COMPILE_SUCCEEDED, "Succeeded");
    }

    /**
     * Called by BlueJ when a compilation attempt completes with errors.
     * Records a {@code COMPILE_FAILED} event with duration and file details.
     *
     * @param e the BlueJ compile event
     */
    public void compileFailed(CompileEvent e) {
        recordCompileLifeCycleEvents(e, EventType.COMPILE_FAILED, "Failed");
    }

    /**
     * Called by BlueJ when a compiler warning is reported for a specific source line.
     * Records a {@code COMPILE_WARNING} event with the line number, message, and character position.
     *
     * @param e the BlueJ compile event containing the warning details
     */
    public void compileWarning(CompileEvent e) {
        HashMap<String, Object> compileWarningDetails = new HashMap<>();

        compileWarningDetails.put("Warning Start Line", e.getErrorLineNumber());
        compileWarningDetails.put("Warning Message", e.getErrorMessage());
        compileWarningDetails.put("Warning Position", e.getErrorPosition());

        sjLogger.recordEvent(EventType.COMPILE_WARNING, compileWarningDetails);
    }

    /**
     * Called by BlueJ when a compiler error is reported for a specific source line.
     * Records a {@code COMPILE_ERROR} event with the line number, message, and character position.
     *
     * @param e the BlueJ compile event containing the error details
     */
    public void compileError(CompileEvent e) {
        HashMap<String, Object> compileErrorDetails = new HashMap<>();

        compileErrorDetails.put("Error Start Line", e.getErrorLineNumber());
        compileErrorDetails.put("Error Message", e.getErrorMessage());
        compileErrorDetails.put("Error Position", e.getErrorPosition());

        sjLogger.recordEvent(EventType.COMPILE_ERROR, compileErrorDetails);
    }
}

package com.spyj.listener;

import java.util.Arrays;
import java.util.HashMap;

import com.spyj.logger.EventType;
import com.spyj.logger.SpyJLogger;

import bluej.extensions2.event.InvocationFinishedEvent;
import bluej.extensions2.event.InvocationFinishedListener;

/**
 * Listens for BlueJ method and constructor invocation events and records them as
 * {@code EXECUTION_SUCCEEDED} or {@code EXECUTION_EXCEPTION} telemetry events.
 * Implements the BlueJ {@link InvocationFinishedListener} API.
 *
 * <p>An invocation is classified as succeeded when the BlueJ exit type is
 * {@link InvocationFinishedEvent.EventType#NORMAL_EXIT}; all other exit types
 * are classified as exceptions.</p>
 *
 * @author Mykel Dedier
 */
public class ExecutionListener implements InvocationFinishedListener {

    /** The central logger bus used to publish captured events. */
    private SpyJLogger sjLogger;

    /**
     * Creates an ExecutionListener that records execution events to the given logger.
     *
     * @param sJLogger the central event logger
     */
    public ExecutionListener(SpyJLogger sJLogger) {
        this.sjLogger = sJLogger;
    }

    /**
     * Called by BlueJ when a method or constructor invocation finishes.
     * Captures the package, class, method, parameters, target object, and exit status,
     * then records either an {@code EXECUTION_SUCCEEDED} or {@code EXECUTION_EXCEPTION} event
     * depending on the BlueJ exit type.
     *
     * @param e the BlueJ invocation finished event
     */
    public void invocationFinished(InvocationFinishedEvent e) {
        try {
            HashMap<String, Object> invocationDetails = new HashMap<>();

            invocationDetails.put("Package", e.getPackage().getName());
            invocationDetails.put("Class", e.getClassName());
            invocationDetails.put("Method", e.getMethodName());
            invocationDetails.put("Parameters", e.getParameters() != null ? Arrays.asList(e.getParameters()) : "[]");
            invocationDetails.put("Target Object", e.getObjectName() != null ? e.getObjectName() : "Static Instance");
            invocationDetails.put("Exit Status", e.getEventType());

            EventType eventType;
            if (e.getEventType() == InvocationFinishedEvent.EventType.NORMAL_EXIT)
                eventType = EventType.EXECUTION_SUCCEEDED;
            else
                eventType = EventType.EXECUTION_EXCEPTION;

            sjLogger.recordEvent(eventType, invocationDetails);
        } catch (Exception exception) {
            System.out.println("Failed To Capture Program Invocation");
        }
    }
}

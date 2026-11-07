package com.spyj.listener;

import java.util.Arrays;
import java.util.HashMap;

import com.spyj.logger.EventType;
import com.spyj.logger.SpyJLogger;

import bluej.extensions2.event.InvocationFinishedEvent;
import bluej.extensions2.event.InvocationFinishedListener;

public class ExecutionListener implements InvocationFinishedListener {

    private SpyJLogger sjLogger;

    public ExecutionListener (SpyJLogger sJLogger) {
        this.sjLogger = sJLogger;
    }

    public void invocationFinished (InvocationFinishedEvent e) {
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
            System.out.println("Failed To Capture Program Incocation");
        }
    }
}

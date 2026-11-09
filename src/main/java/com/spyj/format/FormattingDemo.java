package com.spyj.format;


import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.spyj.logger.EventStore;
import com.spyj.logger.StatsTracker;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.spyj.logger.EventType;
import com.spyj.logger.TelemetryEvent;

//Test JSON formatting with sample  data
public class FormattingDemo{
    public static void main(String[] args) throws IOException,InterruptedException,ExecutionException{
        
        // Start of the simulated 60-second session.
        Instant start = Instant.parse("2026-11-09T10:00:00Z");
        Instant end = start.plusSeconds(60);
        Map<String,Object> details = new LinkedHashMap<>();
        details.put ("Class", "HelloWorld");
        details.put("Class Package", "");
        details.put("Class Compiled", false);

        List<TelemetryEvent> events = new ArrayList<>();
        events.add(new TelemetryEvent(EventType.CLASS_CREATED,details,start.plusSeconds(1)));
        // Simulates defining a method inside HelloWorld.
        Map<String, Object> methodDetails = new LinkedHashMap<>();

        methodDetails.put("Class", "HelloWorld");
        methodDetails.put("Method", "main");

        events.add(new TelemetryEvent(  EventType.METHOD_DEFINED,methodDetails,start.plusSeconds(2)));
        // Simulates a period of typing in the editor.
        Map<String, Object> typing = new LinkedHashMap<>();
        typing.put("Keystroke Count", 120);
        typing.put("Burst Duration", 30000L);
        typing.put("Cadence KPM", 240);
        typing.put("Total Session Keystrokes", 120);

        events.add(new TelemetryEvent(EventType.EDITOR_BURST,typing,start.plusSeconds(31)));

        // Simulates a period when the editor was idle.
        Map<String, Object> idle = new LinkedHashMap<>();
        idle.put("Idle Duration Time", 10000L);

        events.add(new TelemetryEvent(EventType.EDITOR_IDLE, idle,start.plusSeconds(41)));

        //Adds two occurences of the same error
        Map<String,Object> firstError= new LinkedHashMap<>();
        firstError.put("Error Message", "Cannot find symbols");
        firstError.put("Error Start Line",5);

        events.add(new TelemetryEvent(EventType.COMPILE_ERROR, firstError,start.plusSeconds(43)));
        events.add(new TelemetryEvent(EventType.COMPILE_ERROR, firstError,start.plusSeconds(43)));
        
        //Adds a different error
         Map<String,Object> secondError= new LinkedHashMap<>();
         secondError.put("Error Message","';' expected");
         secondError.put("Error Start Line",8);

         events.add(new TelemetryEvent(EventType.COMPILE_ERROR, secondError,start.plusSeconds(43)));
        
         //Records one failes compilation containing these errors
        Map<String,Object> compilation =new LinkedHashMap<>();
        compilation.put("Compile Status","Failed");

        events.add(new TelemetryEvent(EventType.COMPILE_FAILED, compilation,start.plusSeconds(46)));
         // Records another failed compilation.
         events.add(new TelemetryEvent(EventType.COMPILE_FAILED, Map.of("Compile Status","Failed"),start.plusSeconds(51)));

        // Records a successful compilation.
        events.add(new TelemetryEvent(EventType.COMPILE_SUCCEEDED, Map.of("Compile Status","Succeeded"),start.plusSeconds(60)));

        //Stores the sample events and calculates the stattistics
        EventStore store = new EventStore();
        StatsTracker tracker = new StatsTracker();
        
        // Sends the existing events directly to both subscribers.
        //This preserves the controlled timestamps for testing.
        for(TelemetryEvent event: events){
            store.onEvent(event);
            tracker.onEvent(event);
        }

        // Uses the events saved by EventStore.
        events = store.getEvents();
        //Converts the tracked statistics into our report fields
        SessionSummaryBuilder builder= new SessionSummaryBuilder();
        Map<String,Object> summary=builder.build(tracker,"demo-session","placeholder",start,end);
        
        // Sets the output file paths.
        Path jsonOutput = Path.of("target", "sample-report.json");
        Path pdfOutput = Path.of("target", "sample-report.pdf");

        // Creates the export commands.
        Command jsonCommand = new ExportCommand(
        new JSONFormatter(), events, summary, jsonOutput);

        Command pdfCommand = new ExportCommand(
        new PDFFormatter(), events, summary, pdfOutput);

        // Creates a background worker for the exports.
        try (CommandInvoker invoker = new CommandInvoker()) {

      // Queues both commands to run on the worker.
       Future<Void> jsonResult = invoker.submit(jsonCommand);
       Future<Void> pdfResult = invoker.submit(pdfCommand);

    // Waits for JSON to finish and reports any export error.
    jsonResult.get();
    System.out.println("JSON saved to: " + jsonOutput.toAbsolutePath());

    // Waits for PDF to finish and reports any export error.
    pdfResult.get();
    System.out.println( "PDF saved to: " + pdfOutput.toAbsolutePath());
    }
    }
        
}
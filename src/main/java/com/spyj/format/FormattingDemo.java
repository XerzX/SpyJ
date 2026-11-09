package com.spyj.format;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.spyj.logger.EventStore;
import com.spyj.logger.StatsTracker;


import com.spyj.logger.EventType;
import com.spyj.logger.TelemetryEvent;

//Test JSON formatting with sample  data
public class FormattingDemo{
    public static void main(String[] args) throws IOException{
        
        Instant start= Instant.now();
        Map<String,Object> details = new LinkedHashMap<>();
        details.put ("Class", "HelloWorld");
        details.put("Class Package", "");
        details.put("Class Compiled", false);

        List<TelemetryEvent> events = new ArrayList<>();
        events.add(new TelemetryEvent(EventType.CLASS_CREATED,details));
        //Ads two occrences of the same error
        Map<String,Object> firstError= new LinkedHashMap<>();
        firstError.put("Error Message", "Cannot find symbols");
        firstError.put("Error Start Line",5);

        events.add(new TelemetryEvent(EventType.COMPILE_ERROR, firstError));
        events.add(new TelemetryEvent(EventType.COMPILE_ERROR, firstError));
        
        //Adds a different error
         Map<String,Object> secondError= new LinkedHashMap<>();
         secondError.put("Error Message","';' expected");
         secondError.put("Error Start Line",8);

         events.add(new TelemetryEvent(EventType.COMPILE_ERROR, secondError));
        
         //Records one failes compilation containing these errors
         Map<String,Object> compilation =new LinkedHashMap<>();
         compilation.put("Compile Status","Failed");

         events.add(new TelemetryEvent(EventType.COMPILE_FAILED, compilation));

        //Stores the sample events and calculates the stattistics
        EventStore store = new EventStore();
        StatsTracker tracker = new StatsTracker();

        // Sends each event to both objects.
        for (TelemetryEvent event : events) {
             store.onEvent(event);
             tracker.onEvent(event);
}

        Instant end = Instant.now();

        // Uses the events saved by EventStore.
        events = store.getEvents();
        //Converts the tracked statistics into our report fields
        SessionSummaryBuilder builder= new SessionSummaryBuilder();
        Map<String,Object> summary=builder.build(tracker,"demo-session","placeholder",start,end);
              

        //Format the sample data
        LogFormatter formatter = new JSONFormatter();
        byte[]content=formatter.format(events,summary);

        //Saves the sample inside the build folder
        Path output= Path.of("target","sample-report.json");
        Files.createDirectories(output.getParent());
        Files.write(output,content);

        System.out.println("JSON saved to " + output.toAbsolutePath());

        //Creates the PDF Report
        LogFormatter pdfFormatter=  new PDFFormatter();
        byte[]pdfContent= pdfFormatter.format(events,summary);

        //Saves the pdf files
        Path pdfOutput=Path.of("target","sample-report.pdf");
        Files.createDirectories(pdfOutput.getParent());
        Files.write(pdfOutput,pdfContent);

        System.out.println("PDF saved to:" +pdfOutput.toAbsolutePath());

    }



}
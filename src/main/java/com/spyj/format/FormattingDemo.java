package com.spyj.format;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


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

        Instant end= Instant.now();

        Map<String,Object> summary = new LinkedHashMap<>();
        summary.put("session_id","demo-session");
        summary.put("student_id","placeholder");
        summary.put("start_time",start.toString());
        summary.put("end_time",end.toString());
        summary.put("session_duration",java.time.Duration.between(start,end).toMillis());

        //These values are unavailable until activity tracking is ready
        summary.put("active_time",6000L);
        summary.put("idle_time",3000L);

        //This sample contains no compilations or runes
        summary.put("total_compilations",1);
        summary.put("successful_compilations",0);
        summary.put("failed_compilations",1);
        summary.put("total_runs",0);

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
package com.spyj.format;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.spyj.logger.EventType;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import com.spyj.logger.TelemetryEvent;

//Formats telemetry data as a two-page PDF report using the startegy pattern
public class PDFFormatter implements LogFormatter{

    @Override 
    public byte[] format(List<TelemetryEvent> events, Map<String,Object> sessionSummary) throws IOException{
        try(PDDocument document = new PDDocument();
            ByteArrayOutputStream output = new ByteArrayOutputStream()){

            addSummaryPage(document, sessionSummary);
            addEventsPage(document, events);

            //Returns the PDF as a byte array
            document.save(output);
            return output.toByteArray();
        }
    }

    //Creates a session summary page
    private void addSummaryPage(PDDocument document, Map<String,Object> summary ) throws IOException{
        PDPage page=new PDPage(PDRectangle.A4);
        document.addPage(page);

        try (PDPageContentStream content= new PDPageContentStream(document, page)){
        writeLine(content,"SPYJ Session Report", 50,790,20);

        String[][] fields = {
                {"Session ID", "session_id"},
                {"Student ID", "student_id"},
                {"Start time", "start_time"},
                {"End time", "end_time"},
                {"Session duration (ms)", "session_duration"},
                {"Active time (ms)", "active_time"},
                {"Idle time (ms)", "idle_time"},
                {"Total compilations", "total_compilations"},
                {"Successful compilations", "successful_compilations"},
                {"Failed compilations", "failed_compilations"},
                {"Total runs", "total_runs"}
            };
            
            float y= 745;
            //Write each summary value
            for(String[] field: fields){
                Object value= summary.get(field[1]);
                String text=value== null
                    ?"Not available": value.toString();
            
                writeLine(content,field[0]+": "+text,50,y,11);
                y-=28;
            }
            //Gets the compilation counts
            Number total=(Number) summary.get("total_compilations");
            Number successful= (Number) summary.get("successful_compilations");

            String successRate="Not available";

            //Calculates the percentage when compilation exists
            if(total!= null && successful!=null && total.longValue()>0){
                    double percentage=successful.doubleValue()/total.doubleValue()*100;
                    successRate=String.format(java.util.Locale.ROOT,"%.1f%%", percentage);
            }
            writeLine(content, "Compilation success rate: "+successRate,50,y,11);
            
            //Draws the compilation chart
            drawCompilationChart(content, summary);
            drawActivityChart(content,summary);
            writeLine(content,"Page 1 of 2", 50,40,10);
        }
    }

    // Draws successful and failed compilation counts.
    private void drawCompilationChart( PDPageContentStream content,Map<String, Object> summary) throws IOException {

        writeLine(content, "Compilation Results", 50, 380, 16);

        Number successful =
                (Number) summary.get("successful_compilations");
        Number failed =
                (Number) summary.get("failed_compilations");

        if (successful == null || failed == null) {
            writeLine(content, "Compilation data unavailable.", 50, 350, 11);
            return;
        }

        long successCount = successful.longValue();
        long failedCount = failed.longValue();

        if (successCount < 0 || failedCount < 0) {
            writeLine(content, "Invalid compilation counts.", 50, 350, 11);
            return;
        }

        long maximum = Math.max(successCount, failedCount);

        if (maximum == 0) {
            writeLine(content, "No compilations recorded.", 50, 350, 11);
            return;
        }

        // Both bars use the same scale, starting from zero.
        float successWidth = (float) (successCount / (double) maximum * 280);
        float failedWidth = (float) (failedCount / (double) maximum * 280);

        writeLine(content, "Successful", 50, 340, 11);
        writeLine(content, "Failed", 50, 300, 11);

        content.saveGraphicsState();

        // Draws the successful compilation bar.
        content.setNonStrokingColor(0.15f, 0.55f, 0.35f);
        if (successCount > 0) {
            content.addRect(150, 334, successWidth, 18);
            content.fill();
        }

        // Draws the failed compilation bar.
        content.setNonStrokingColor(0.75f, 0.20f, 0.20f);
        if (failedCount > 0) {
            content.addRect(150, 294, failedWidth, 18);
            content.fill();
        }

        content.restoreGraphicsState();

        // Shows the exact counts beside the bars.
        writeLine(content, Long.toString(successCount), 445, 340, 11);
        writeLine(content, Long.toString(failedCount), 445, 300, 11);

        writeLine(content, "Bar length represents compilation count.", 50, 270, 9);
     }


    //Creates the second page with recorded events
    private void addEventsPage(PDDocument document,List<TelemetryEvent>events) throws IOException{
        
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        // Counts each error message across all events.
        Map<String, Integer> errorCounts = new LinkedHashMap<>();

        for (TelemetryEvent event : events) {
            if (event.getEventType() == EventType.COMPILE_ERROR) {
                Object message = event.getEventDetails().get("Error Message");

                String error = message == null
                        ? "Unknown error"
                        : message.toString();

                errorCounts.put(
                        error,
                        errorCounts.getOrDefault(error, 0) + 1);
            }
        }

        try (PDPageContentStream content =
                new PDPageContentStream(document, page)) {

            writeLine(content, "Event Timeline", 50, 790, 20);

            float y = 755;

            if (events.isEmpty()) {
                writeLine(content, "No events recorded.", 50, y, 11);
            }

            // Leaves room for the error table below.
            int displayed = Math.min(events.size(), 10);

            for (int i = 0; i < displayed; i++) {
                TelemetryEvent event = events.get(i);

                String text = event.getTimestamp().toString() + " | " + event.getEventType().name();
                //Adds the class name when available
                Object className=event.getEventDetails().get("Class");
                if(className !=null){
                    text+=" | "+className.toString();
                }

                writeLine(content, text, 50, y, 9);
                y -= 24;
            }

            if (events.size() > displayed) {
                writeLine(content, "Showing the first 10 events. See JSON for all events.",50, y, 10);
            }

            writeLine(content, "Error Frequency", 50, 465, 16);
            writeLine(content, "Count", 50, 435, 11);
            writeLine(content, "Error message", 110, 435, 11);

            // Draws a line below the table headings.
            content.moveTo(50, 425);
            content.lineTo(545, 425);
            content.stroke();

            y = 405;

            if (errorCounts.isEmpty()) {
                writeLine(
                        content,
                        "No compilation errors recorded.",
                        50, y, 11);
            }

            int rows = 0;

            // Shows up to 10 different error messages.
            for (Map.Entry<String, Integer> entry : errorCounts.entrySet()) {
                if (rows == 10) {
                    break;
                }

                writeLine(content, entry.getValue().toString(),50, y, 10);
                writeLine(content, entry.getKey(), 110, y, 10);

                y -= 24;
                rows++;
            }

            if (errorCounts.size() > rows) {
                writeLine( content,"More error messages are available in the JSON events.",50, y, 10);
            }

            writeLine(content,"Long messages are shortened. See JSON for full details.",50, 100, 9);

            writeLine(content, "Page 2 of 2", 50, 40, 10);
        }
    }

    //Draws active and the idle time
    private void drawActivityChart(PDPageContentStream content,Map<String,Object>summary) throws IOException{
        writeLine(content, "Active and Idle Time", 50,230,16);
        
        Number active = (Number) summary.get("active_time");
        Number idle = (Number) summary.get("idle_time");

        if (active == null || idle == null) {
            writeLine(content, "Activity data unavailable.", 50, 200, 11);
            return;
        }

        long activeTime = active.longValue();
        long idleTime = idle.longValue();

        if (activeTime < 0 || idleTime < 0) {
            writeLine(content, "Invalid activity durations.", 50, 200, 11);
            return;
        }

        long maximum = Math.max(activeTime, idleTime);

        if (maximum == 0) {
            writeLine(content, "No activity duration recorded.", 50, 200, 11);
            return;
        }

        // Uses the same scale for both bars.
        float activeWidth = (float) (activeTime / (double) maximum * 280);
        float idleWidth = (float) (idleTime / (double) maximum * 280);

        writeLine(content, "Active", 50, 190, 11);
        writeLine(content, "Idle", 50, 150, 11);

        content.saveGraphicsState();

        // Draws active time in blue.
        content.setNonStrokingColor(0.20f, 0.45f, 0.75f);
        if (activeTime > 0) {
            content.addRect(150, 184, activeWidth, 18);
            content.fill();
        }

        // Draws idle time in grey.
        content.setNonStrokingColor(0.55f, 0.55f, 0.55f);
        if (idleTime > 0) {
            content.addRect(150, 144, idleWidth, 18);
            content.fill();
        }

        content.restoreGraphicsState();

        writeLine(content, activeTime + " ms", 445, 190, 10);
        writeLine(content, idleTime + " ms", 445, 150, 10);

        writeLine(content, "Bar length represents time in milliseconds.", 50, 115, 9);
    }

    // Writes one line of text that fits within the page.
    private void writeLine(PDPageContentStream content, String text, float x, float y, int size) throws IOException {

        PDType1Font font =new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        // Uses basic characters supported by this font.
        String safeText = text.replaceAll("[^\\x20-\\x7E]", "?");

        float availableWidth = PDRectangle.A4.getWidth() - x - 50;

        // Shortens long text to prevent it going off the page.
        if (font.getStringWidth(safeText) / 1000 * size > availableWidth) {
            while (!safeText.isEmpty()&& font.getStringWidth(safeText + "...") / 1000 * size> availableWidth) {
                safeText = safeText.substring(0, safeText.length() - 1);
            }
            safeText += "...";
        }

        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(safeText);
        content.endText();
    }
}

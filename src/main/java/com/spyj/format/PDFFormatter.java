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

        try (PDPageContentStream content= 
            new PDPageContentStream(document, page)){
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
            writeLine(content, "Page 1 of 2", 50, 40, 10);
            }
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

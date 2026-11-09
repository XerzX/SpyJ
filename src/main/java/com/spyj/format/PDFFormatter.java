package com.spyj.format;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.spyj.logger.EventType;
import com.spyj.logger.TelemetryEvent;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

// Formats telemetry data as a PDF report using the Strategy pattern.
public class PDFFormatter implements LogFormatter {

    @Override
    public byte[] format(List<TelemetryEvent> events, Map<String, Object> sessionSummary) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            addSummaryPage(document, sessionSummary);
            addEventsPage(document, events);

            document.save(output);
            return output.toByteArray();
        }
    }

    // Creates the first page containing session statistics and charts.
    private void addSummaryPage(PDDocument document, Map<String, Object> summary) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            writeLine(content, "SPYJ Session Report", 50, 790, 20);

            String[][] fields = {
                {"Session ID", "session_id"},
                {"Student ID", "student_id"},
                {"Start time", "start_time"},
                {"End time", "end_time"},
                {"Session duration (ms)", "session_duration"},
                {"Active typing time (ms)", "active_typing_time"},
                {"Recorded editor idle time (ms)", "recorded_editor_idle_time"},
                {"Total compilations", "total_compilations"},
                {"Successful compilations", "successful_compilations"},
                {"Failed compilations", "failed_compilations"},
                {"Total runs", "total_runs"}
            };

            float y = 745;

            // Displays each session statistic.
            for (String[] field : fields) {
                Object value = summary.get(field[1]);
                String text = value == null ? "Not available" : value.toString();
                writeLine(content, field[0] + ": " + text, 50, y, 11);
                y -= 26;
            }

            // Calculates the compilation success percentage.
            Number total = getNumber(summary, "total_compilations");
            Number successful = getNumber(summary, "successful_compilations");
            String successRate = "Not available";

            if (total != null && successful != null && total.longValue() > 0) {
                double percentage = successful.doubleValue() / total.doubleValue() * 100;
                successRate = String.format(java.util.Locale.ROOT, "%.1f%%", percentage);
            }

            writeLine(content, "Compilation success rate: " + successRate, 50, 440, 11);

            drawCompilationChart(content, summary);
            drawActivityChart(content, summary);

            writeLine(content, "Page 1 of 2", 50, 40, 10);
        }
    }

    // Draws successful and failed compilation counts.
    private void drawCompilationChart(PDPageContentStream content, Map<String, Object> summary) throws IOException {
        writeLine(content, "Compilation Results", 50, 400, 16);

        Number successful = getNumber(summary, "successful_compilations");
        Number failed = getNumber(summary, "failed_compilations");

        if (successful == null || failed == null) {
            writeLine(content, "Compilation data unavailable.", 50, 370, 11);
            return;
        }

        long successCount = successful.longValue();
        long failedCount = failed.longValue();

        if (successCount < 0 || failedCount < 0) {
            writeLine(content, "Invalid compilation counts.", 50, 370, 11);
            return;
        }

        long maximum = Math.max(successCount, failedCount);

        if (maximum == 0) {
            writeLine(content, "No compilations recorded.", 50, 370, 11);
            return;
        }

        float successWidth = (float) (successCount / (double) maximum * 280);
        float failedWidth = (float) (failedCount / (double) maximum * 280);

        writeLine(content, "Successful", 50, 360, 11);
        writeLine(content, "Failed", 50, 320, 11);

        content.saveGraphicsState();

        // Green represents successful compilations.
        content.setNonStrokingColor(0.15f, 0.55f, 0.35f);
        if (successCount > 0) {
            content.addRect(150, 354, successWidth, 18);
            content.fill();
        }

        // Red represents failed compilations.
        content.setNonStrokingColor(0.75f, 0.20f, 0.20f);
        if (failedCount > 0) {
            content.addRect(150, 314, failedWidth, 18);
            content.fill();
        }

        content.restoreGraphicsState();

        writeLine(content, Long.toString(successCount), 445, 360, 11);
        writeLine(content, Long.toString(failedCount), 445, 320, 11);
        writeLine(content, "Bar length represents compilation count.", 50, 290, 9);
    }

    // Draws active coding and idle time when measurements are available.
    private void drawActivityChart(PDPageContentStream content, Map<String, Object> summary) throws IOException {
        Number active = getNumber(summary, "active_time");
        Number idle = getNumber(summary, "idle_time");
        boolean fullSession = active != null && idle != null;

        // Uses editor measurements if full-session data is unavailable.
        if (!fullSession) {
            active = getNumber(summary, "active_typing_time");
            idle = getNumber(summary, "recorded_editor_idle_time");
        }

        String heading = fullSession ? "Total Active Coding and Idle Time" : "Recorded Editor Typing and Idle Time";
        writeLine(content, heading, 50, 250, 16);

        if (active == null || idle == null) {
            writeLine(content, "Activity data unavailable.", 50, 220, 11);
            return;
        }

        long activeTime = active.longValue();
        long idleTime = idle.longValue();

        if (activeTime < 0 || idleTime < 0) {
            writeLine(content, "Invalid activity durations.", 50, 220, 11);
            return;
        }

        long maximum = Math.max(activeTime, idleTime);

        if (maximum == 0) {
            writeLine(content, "No activity duration recorded.", 50, 220, 11);
            return;
        }

        float activeWidth = (float) (activeTime / (double) maximum * 280);
        float idleWidth = (float) (idleTime / (double) maximum * 280);

        String activeLabel = fullSession ? "Active" : "Typing";
        String idleLabel = fullSession ? "Idle" : "Editor idle";

        writeLine(content, activeLabel, 50, 210, 11);
        writeLine(content, idleLabel, 50, 170, 11);

        content.saveGraphicsState();

        // Blue represents active time.
        content.setNonStrokingColor(0.20f, 0.45f, 0.75f);
        if (activeTime > 0) {
            content.addRect(150, 204, activeWidth, 18);
            content.fill();
        }

        // Grey represents idle time.
        content.setNonStrokingColor(0.55f, 0.55f, 0.55f);
        if (idleTime > 0) {
            content.addRect(150, 164, idleWidth, 18);
            content.fill();
        }

        content.restoreGraphicsState();

        writeLine(content, activeTime + " ms", 445, 210, 10);
        writeLine(content, idleTime + " ms", 445, 170, 10);
        writeLine(content, "Bar length represents time in milliseconds.", 50, 135, 9);

        if (!fullSession) {
            writeLine(content, "Full-session active and idle times are not yet available.", 50, 115, 9);
        }
    }

    // Creates the second page containing progression events and compilation errors.
    private void addEventsPage(PDDocument document, List<TelemetryEvent> events) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        Map<String, Integer> errorCounts = new LinkedHashMap<>();
        List<TelemetryEvent> progression = new ArrayList<>();

        for (TelemetryEvent event : events) {
            EventType type = event.getEventType();

            // Counts compilation errors.
            if (type == EventType.COMPILE_ERROR) {
                Object message = event.getEventDetails().get("Error Message");
                String error = message == null ? "Unknown error" : message.toString();
                errorCounts.put(error, errorCounts.getOrDefault(error, 0) + 1);
            }

            // Records class creation and method definition events.
            if (type == EventType.CLASS_CREATED || type == EventType.METHOD_DEFINED) {
                progression.add(event);
            }
        }

        progression.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            writeLine(content, "Class and Method Progression Timeline", 50, 790, 16);

            float y = 755;

            if (progression.isEmpty()) {
                writeLine(content, "No class or method definitions recorded.", 50, y, 11);
            } else {
                int displayed = Math.min(progression.size(), 9);

                for (int i = 0; i < displayed; i++) {
                    TelemetryEvent event = progression.get(i);
                    String text = event.getTimestamp() + " | " + event.getEventType().name();

                    Object className = event.getEventDetails().get("Class");
                    Object methodName = event.getEventDetails().get("Method");

                    if (className != null) {
                        text += " | " + className;
                    }

                    if (methodName != null) {
                        text += " | " + methodName;
                    }

                    writeLine(content, text, 50, y, 9);
                    y -= 18;
                }

                if (progression.size() > displayed) {
                    writeLine(content, "More progression events are available in JSON.", 50, y, 9);
                }
            }

            // Displays compilation error frequencies.
            writeLine(content, "Error Frequency", 50, 550, 16);
            writeLine(content, "Count", 50, 525, 11);
            writeLine(content, "Error message", 110, 525, 11);

            content.moveTo(50, 515);
            content.lineTo(545, 515);
            content.stroke();

            y = 495;

            if (errorCounts.isEmpty()) {
                writeLine(content, "No compilation errors recorded.", 50, y, 11);
            }

            int rows = 0;

            // Displays up to 8 different errors.
            for (Map.Entry<String, Integer> entry : errorCounts.entrySet()) {
                if (rows == 8) {
                    break;
                }

                writeLine(content, entry.getValue().toString(), 50, y, 10);
                writeLine(content, entry.getKey(), 110, y, 10);

                y -= 18;
                rows++;
            }

            if (errorCounts.size() > rows) {
                writeLine(content, "Additional error messages are available in JSON.", 50, y, 9);
            }

            drawCompilationIntervals(content, events);

            writeLine(content, "Long messages are shortened. See JSON for full details.", 50, 85, 9);
            writeLine(content, "Page 2 of 2", 50, 40, 10);
        }
    }

    // Calculates intervals between compilations.
    private void drawCompilationIntervals(PDPageContentStream content, List<TelemetryEvent> events) throws IOException {
        writeLine(content, "Compilation Intervals", 50, 305, 16);

        List<Instant> compileTimes = new ArrayList<>();

        for (TelemetryEvent event : events) {
            EventType type = event.getEventType();

            if (type == EventType.COMPILE_SUCCEEDED || type == EventType.COMPILE_FAILED) {
                compileTimes.add(event.getTimestamp());
            }
        }

        Collections.sort(compileTimes);

        if (compileTimes.size() < 2) {
            writeLine(content, "At least two compilations are required.", 50, 275, 11);
            return;
        }

        long total = 0;
        int intervalCount = compileTimes.size() - 1;

        for (int i = 1; i < compileTimes.size(); i++) {
            long interval = Duration.between(compileTimes.get(i - 1), compileTimes.get(i)).toMillis();
            total += interval;

            // Displays the first three intervals.
            if (i <= 3) {
                writeLine(content, "Compilation " + i + " to " + (i + 1) + ": " + interval + " ms",
                        50, 280 - ((i - 1) * 20), 11);
            }
        }

        double average = total / (double) intervalCount;
        writeLine(content, String.format(java.util.Locale.ROOT, "Average interval: %.2f ms", average),
                50, 190, 11);

        if (intervalCount > 3) {
            writeLine(content, "Additional compilation timestamps are in JSON.", 50, 160, 9);
        }
    }

    // Retrieves numeric values from the summary.
    private Number getNumber(Map<String, Object> summary, String key) {
        Object value = summary.get(key);
        return value instanceof Number ? (Number) value : null;
    }

    // Writes text while preventing it from extending beyond the page.
    private void writeLine(PDPageContentStream content, String text, float x, float y, int size) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        String safeText = text.replaceAll("[^\\x20-\\x7E]", "?");
        float availableWidth = PDRectangle.A4.getWidth() - x - 50;

        if (font.getStringWidth(safeText) / 1000 * size > availableWidth) {
            while (!safeText.isEmpty() &&
                   font.getStringWidth(safeText + "...") / 1000 * size > availableWidth) {
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

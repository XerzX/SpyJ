package com.spyj.format;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.spyj.logger.EventStore;
import com.spyj.logger.StatsTracker;
import com.spyj.logger.TelemetryEvent;

// Exports recorded telemetry data as JSON and PDF reports.
public class ReportExporter {

    public void export(EventStore store, StatsTracker tracker,String sessionId, String studentId,Instant start, Instant end) throws IOException {

        // Retrieves the events collected during the session.
        List<TelemetryEvent> events = store.getEvents();

        // Creates the session summary.
        SessionSummaryBuilder builder = new SessionSummaryBuilder();
        Map<String, Object> summary = builder.build(tracker, sessionId, studentId, start, end);

        // Uses the Strategy pattern to format both reports.
        LogFormatter jsonFormatter = new JSONFormatter();
        LogFormatter pdfFormatter = new PDFFormatter();

        byte[] jsonData = jsonFormatter.format(events, summary);
        byte[] pdfData = pdfFormatter.format(events, summary);

        // Creates the reports folder if it does not exist.
        Path folder = Path.of("target", "reports");
        Files.createDirectories(folder);

        // Saves both reports.
        Path jsonPath = folder.resolve(sessionId + ".json");
        Path pdfPath = folder.resolve(sessionId + ".pdf");

        Files.write(jsonPath, jsonData);
        Files.write(pdfPath, pdfData);

        System.out.println("JSON saved to: " + jsonPath.toAbsolutePath());
        System.out.println("PDF saved to: " + pdfPath.toAbsolutePath());
    }
}

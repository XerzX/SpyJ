package com.spyj.format;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import com.spyj.logger.EventType;
import com.spyj.logger.TelemetryEvent;

class PDFFormatterTest {
    private static final Instant START = Instant.parse("2026-11-09T10:00:00Z");

    private final PDFFormatter formatter = new PDFFormatter();

    private Map<String, Object> fullSummary() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("session_id", "demo-session");
        s.put("student_id", "placeholder");
        s.put("start_time", "2026-11-09T10:00:00Z");
        s.put("end_time", "2026-11-09T10:01:00Z");
        s.put("session_duration", 60000L);
        s.put("active_time", null);
        s.put("idle_time", null);
        s.put("active_typing_time", 30000L);
        s.put("recorded_editor_idle_time", 10000L);
        s.put("total_compilations", 3L);
        s.put("successful_compilations", 1L);
        s.put("failed_compilations", 2L);
        s.put("total_runs", 0L);
        return s;
    }

    private Map<String, Object> details(String... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private TelemetryEvent event(EventType type, long secondsAfterStart, Map<String, Object> details) {
        return new TelemetryEvent(type, details, START.plusSeconds(secondsAfterStart));
    }

    private String text(byte[] pdf, int page) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            return stripper.getText(document);
        }
    }

    private String page1(Map<String, Object> summary) throws IOException {
        return text(formatter.format(new ArrayList<>(), summary), 1);
    }

    private String page2(List<TelemetryEvent> events) throws IOException {
        return text(formatter.format(events, fullSummary()), 2);
    }

    // Structure and Page 1

    @Test
    void producesTwoPagePdf() throws IOException {
        byte[] pdf = formatter.format(new ArrayList<>(), fullSummary());

        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.ISO_8859_1));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(2, document.getNumberOfPages());
        }
    }

    @Test
    void firstPageShowsSessionStatistics() throws IOException {
        String text = page1(fullSummary());

        assertTrue(text.contains("SPYJ Session Report"));
        assertTrue(text.contains("Session ID: demo-session"));
        assertTrue(text.contains("Student ID: placeholder"));
        assertTrue(text.contains("Total compilations: 3"));
        assertTrue(text.contains("Successful compilations: 1"));
        assertTrue(text.contains("Failed compilations: 2"));
        assertTrue(text.contains("Total runs: 0"));
        assertTrue(text.contains("Page 1 of 2"));
    }

    @Test
    void compilationSuccessRateIsCalculated() throws IOException {
        assertTrue(page1(fullSummary()).contains("Compilation success rate: 33.3%"));
    }

    @Test
    void missingValuesShowNotAvailable() throws IOException {
        String text = page1(new LinkedHashMap<>());

        assertTrue(text.contains("Session ID: Not available"));
        assertTrue(text.contains("Compilation success rate: Not available"));
        assertTrue(text.contains("Compilation data unavailable."));
        assertTrue(text.contains("Activity data unavailable."));
    }

    @Test
    void compilationChartIsLabelled() throws IOException {
        String text = page1(fullSummary());

        assertTrue(text.contains("Compilation Results"));
        assertTrue(text.contains("Successful"));
        assertTrue(text.contains("Failed"));
        assertTrue(text.contains("Bar length represents compilation count."));
    }

    @Test
    void invalidCompilationCountsShowMessages() throws IOException {
        Map<String, Object> zero = fullSummary();
        zero.put("successful_compilations", 0L);
        zero.put("failed_compilations", 0L);
        assertTrue(page1(zero).contains("No compilations recorded."));

        Map<String, Object> negative = fullSummary();
        negative.put("successful_compilations", -1L);
        assertTrue(page1(negative).contains("Invalid compilation counts."));
    }

    @Test
    void activityChartUsesFullSessionTimesWhenAvailable() throws IOException {
        Map<String, Object> summary = fullSummary();
        summary.put("active_time", 40000L);
        summary.put("idle_time", 20000L);

        String text = page1(summary);

        assertTrue(text.contains("Total Active Coding and Idle Time"));
        assertTrue(text.contains("40000 ms"));
        assertTrue(text.contains("20000 ms"));
        assertFalse(text.contains("not yet available"));
    }

    @Test
    void activityChartFallsBackToEditorTimes() throws IOException {
        String text = page1(fullSummary()); // active_time and idle_time are null

        assertTrue(text.contains("Recorded Editor Typing and Idle Time"));
        assertTrue(text.contains("30000 ms"));
        assertTrue(text.contains("10000 ms"));
        assertTrue(text.contains("Full-session active and idle times are not yet available."));
    }

    @Test
    void invalidActivityDurationsShowMessages() throws IOException {
        Map<String, Object> zero = fullSummary();
        zero.put("active_typing_time", 0L);
        zero.put("recorded_editor_idle_time", 0L);
        assertTrue(page1(zero).contains("No activity duration recorded."));

        Map<String, Object> negative = fullSummary();
        negative.put("active_typing_time", -5L);
        assertTrue(page1(negative).contains("Invalid activity durations."));
    }

    // Page 2: Progression

    @Test
    void progressionTimelineIsSortedByTime() throws IOException {
        List<TelemetryEvent> events = List.of(
                event(EventType.METHOD_DEFINED, 2, details("Class", "HelloWorld", "Method", "main")),
                event(EventType.CLASS_CREATED, 1, details("Class", "HelloWorld")));

        String text = page2(events);

        assertTrue(text.contains("Class and Method Progression Timeline"));
        assertTrue(text.contains("2026-11-09T10:00:01Z | CLASS_CREATED | HelloWorld"));
        assertTrue(text.contains("2026-11-09T10:00:02Z | METHOD_DEFINED | HelloWorld | main"));
        assertTrue(text.indexOf("CLASS_CREATED") < text.indexOf("METHOD_DEFINED"));
        assertTrue(text.contains("Page 2 of 2"));
    }

    @Test
    void progressionListIsLimitedToNineEvents() throws IOException {
        List<TelemetryEvent> events = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            events.add(event(EventType.CLASS_CREATED, i, details("Class", "Class" + i)));
        }

        String text = page2(events);

        assertTrue(text.contains("Class9"));
        assertFalse(text.contains("Class10"));
        assertTrue(text.contains("More progression events are available in JSON."));
    }

    @Test
    void emptyEventsShowPlaceholders() throws IOException {
        String text = page2(new ArrayList<>());

        assertTrue(text.contains("No class or method definitions recorded."));
        assertTrue(text.contains("No compilation errors recorded."));
        assertTrue(text.contains("At least two compilations are required."));
    }

    // Page 2: Error frequency 

    @Test
    void errorFrequencyListsErrorMessages() throws IOException {
        List<TelemetryEvent> events = List.of(
                event(EventType.COMPILE_ERROR, 1, details("Error Message", "Cannot find symbols")),
                event(EventType.COMPILE_ERROR, 2, details("Error Message", "Cannot find symbols")),
                event(EventType.COMPILE_ERROR, 3, details("Error Message", "';' expected")),
                event(EventType.COMPILE_ERROR, 4, details())); // no message

        String text = page2(events);

        assertTrue(text.contains("Error Frequency"));
        assertTrue(text.contains("Cannot find symbols"));
        assertTrue(text.contains("';' expected"));
        assertTrue(text.contains("Unknown error"));
    }

    @Test
    void errorListIsLimitedToEightMessages() throws IOException {
        List<TelemetryEvent> events = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            events.add(event(EventType.COMPILE_ERROR, i, details("Error Message", "Error " + i)));
        }

        String text = page2(events);

        assertTrue(text.contains("Error 8"));
        assertFalse(text.contains("Error 9"));
        assertTrue(text.contains("Additional error messages are available in JSON."));
    }

    @Test
    void errorMessagesAreSanitisedAndShortened() throws IOException {
        List<TelemetryEvent> events = List.of(
                event(EventType.COMPILE_ERROR, 1, details("Error Message", "caf\u00e9 error")),
                event(EventType.COMPILE_ERROR, 2, details("Error Message", "x".repeat(300))));

        String text = page2(events);

        assertTrue(text.contains("caf? error"));      // non-ASCII replaced by '?'
        assertTrue(text.contains("..."));             // long text shortened
        assertFalse(text.contains("x".repeat(300)));
    }

    //Page 2: Compilation intervals 

    @Test
    void compilationIntervalsAreCalculated() throws IOException {
        List<TelemetryEvent> events = List.of(
                event(EventType.COMPILE_FAILED, 46, details()),
                event(EventType.COMPILE_FAILED, 51, details()),
                event(EventType.COMPILE_SUCCEEDED, 60, details()));

        String text = page2(events);

        assertTrue(text.contains("Compilation 1 to 2: 5000 ms"));
        assertTrue(text.contains("Compilation 2 to 3: 9000 ms"));
        assertTrue(text.contains("Average interval: 7000.00 ms"));
        assertFalse(text.contains("Additional compilation timestamps"));
    }

    @Test
    void intervalsAreSortedAndOnlyFirstThreeShown() throws IOException {
        List<TelemetryEvent> events = List.of(   // deliberately out of order
                event(EventType.COMPILE_FAILED, 40, details()),
                event(EventType.COMPILE_FAILED, 0, details()),
                event(EventType.COMPILE_SUCCEEDED, 80, details()),
                event(EventType.COMPILE_FAILED, 10, details()),
                event(EventType.COMPILE_FAILED, 20, details()));

        String text = page2(events);

        assertTrue(text.contains("Compilation 1 to 2: 10000 ms"));
        assertTrue(text.contains("Compilation 3 to 4: 20000 ms"));
        assertFalse(text.contains("Compilation 4 to 5"));
        assertTrue(text.contains("Average interval: 20000.00 ms"));
        assertTrue(text.contains("Additional compilation timestamps are in JSON."));
    }
}
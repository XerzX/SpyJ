package com.spyj.format;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spyj.logger.EventStore;
import com.spyj.logger.EventType;
import com.spyj.logger.StatsTracker;
import com.spyj.logger.TelemetryEvent;

/** Unit tests for ReportExporter (writes target/reports/<sessionId>.json and .pdf). */
class ReportExporterTest {
    private static final String SESSION_ID = "unit-test-session";
    private static final Instant START = Instant.parse("2026-11-09T10:00:00Z");
    private static final Instant END = START.plusSeconds(60);

    private final Path jsonPath = Path.of("target", "reports", SESSION_ID + ".json");
    private final Path pdfPath = Path.of("target", "reports", SESSION_ID + ".pdf");

    private EventStore store;
    private StatsTracker tracker;
    private ReportExporter exporter;

    @BeforeEach
    void setUp() throws IOException {
        Files.deleteIfExists(jsonPath);
        Files.deleteIfExists(pdfPath);
        store = new EventStore();
        tracker = new StatsTracker();
        exporter = new ReportExporter();
    }

    @AfterEach
    void cleanUp() throws IOException {
        Files.deleteIfExists(jsonPath);
        Files.deleteIfExists(pdfPath);
    }

    private void record(EventType type, long secondsAfterStart) {
        TelemetryEvent event = new TelemetryEvent(type, new HashMap<>(), START.plusSeconds(secondsAfterStart));
        store.onEvent(event);
        tracker.onEvent(event);
    }

    private JsonObject readJson() throws IOException {
        return JsonParser.parseString(Files.readString(jsonPath)).getAsJsonObject();
    }

    @Test
    void writesJsonAndPdfNamedAfterTheSession() throws IOException {
        record(EventType.COMPILE_FAILED, 10);
        record(EventType.COMPILE_SUCCEEDED, 20);

        exporter.export(store, tracker, SESSION_ID, "placeholder", START, END);

        JsonObject json = readJson();
        assertEquals(SESSION_ID, json.get("session_id").getAsString());
        assertEquals("placeholder", json.get("student_id").getAsString());
        assertEquals(2L, json.get("total_compilations").getAsLong());
        assertEquals(1L, json.get("successful_compilations").getAsLong());
        assertEquals(1L, json.get("failed_compilations").getAsLong());
        assertEquals(60000L, json.get("session_duration").getAsLong());
        assertEquals(2, json.getAsJsonArray("events").size());

        try (PDDocument document = Loader.loadPDF(Files.readAllBytes(pdfPath))) {
            assertEquals(2, document.getNumberOfPages());
        }
    }

    @Test
    void emptySessionStillProducesBothReports() throws IOException {
        exporter.export(store, tracker, SESSION_ID, "placeholder", START, END);

        assertTrue(Files.exists(pdfPath));
        assertEquals(0, readJson().getAsJsonArray("events").size());
    }

    @Test
    void exportingAgainReplacesThePreviousReports() throws IOException {
        exporter.export(store, tracker, SESSION_ID, "first-student", START, END);
        exporter.export(store, tracker, SESSION_ID, "second-student", START, END);

        assertEquals("second-student", readJson().get("student_id").getAsString());
    }

    @Test
    void endBeforeStartIsRejectedAndNothingIsWritten() {
        assertThrows(IllegalArgumentException.class,
                () -> exporter.export(store, tracker, SESSION_ID, "placeholder", END, START));

        assertFalse(Files.exists(jsonPath));
        assertFalse(Files.exists(pdfPath));
    }
}
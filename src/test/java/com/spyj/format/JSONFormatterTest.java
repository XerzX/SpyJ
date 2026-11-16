package com.spyj.format;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spyj.logger.EventType;
import com.spyj.logger.TelemetryEvent;

class JSONFormatterTest {
    private final JSONFormatter formatter = new JSONFormatter();

    private TelemetryEvent event(EventType type, String time, Map<String, Object> details) {
        return new TelemetryEvent(type, details, Instant.parse(time));
    }

    private Map<String, Object> summary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("session_id", "s1");
        summary.put("student_id", "placeholder");
        summary.put("total_compilations", 3L);
        summary.put("active_time", null);
        return summary;
    }

    private JsonObject parse(List<TelemetryEvent> events, Map<String, Object> summary) throws IOException {
        byte[] bytes = formatter.format(events, summary);
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void outputIsValidJsonWithSummaryFields() throws IOException {
        JsonObject json = parse(new ArrayList<>(), summary());

        assertEquals("s1", json.get("session_id").getAsString());
        assertEquals("placeholder", json.get("student_id").getAsString());
        assertEquals(3L, json.get("total_compilations").getAsLong());
    }

    @Test
    void eventsAreListedWithTimestampTypeAndDetails() throws IOException {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("Class", "HelloWorld");
        List<TelemetryEvent> events = List.of(event(EventType.CLASS_CREATED, "2026-11-09T10:00:01Z", details));

        JsonArray array = parse(events, summary()).getAsJsonArray("events");
        JsonObject first = array.get(0).getAsJsonObject();

        assertEquals(1, array.size());
        assertEquals("2026-11-09T10:00:01Z", first.get("timestamp").getAsString());
        assertEquals("CLASS_CREATED", first.get("type").getAsString());
        assertEquals("HelloWorld", first.getAsJsonObject("details").get("Class").getAsString());
    }

    @Test
    void nullSummaryValuesAreKept() throws IOException {
        JsonObject json = parse(new ArrayList<>(), summary());

        assertTrue(json.has("active_time"));
        assertTrue(json.get("active_time").isJsonNull());
    }

    @Test
    void noEventsGivesEmptyArray() throws IOException {
        JsonObject json = parse(new ArrayList<>(), summary());

        assertEquals(0, json.getAsJsonArray("events").size());
    }

    @Test
    void eventOrderIsPreserved() throws IOException {
        List<TelemetryEvent> events = List.of(
                event(EventType.PROJECT_OPENED, "2026-11-09T10:00:01Z", new LinkedHashMap<>()),
                event(EventType.CLASS_CREATED, "2026-11-09T10:00:02Z", new LinkedHashMap<>()),
                event(EventType.COMPILE_FAILED, "2026-11-09T10:00:03Z", new LinkedHashMap<>()));

        JsonArray array = parse(events, summary()).getAsJsonArray("events");

        assertEquals("PROJECT_OPENED", array.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("CLASS_CREATED", array.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("COMPILE_FAILED", array.get(2).getAsJsonObject().get("type").getAsString());
    }

    @Test
    void outputIsPrettyPrintedUtf8() throws IOException {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("Error Message", "caf\u00e9 error");
        List<TelemetryEvent> events = List.of(event(EventType.COMPILE_ERROR, "2026-11-09T10:00:01Z", details));

        byte[] bytes = formatter.format(events, summary());
        String text = new String(bytes, StandardCharsets.UTF_8);
        JsonObject json = JsonParser.parseString(text).getAsJsonObject();

        assertTrue(text.contains("\n"));
        assertEquals("caf\u00e9 error", json.getAsJsonArray("events").get(0).getAsJsonObject()
                .getAsJsonObject("details").get("Error Message").getAsString());
    }

    @Test
    void summaryMapIsNotModified() throws IOException {
        Map<String, Object> summary = summary();

        formatter.format(new ArrayList<>(), summary);

        assertFalse(summary.containsKey("events"));
    }
}
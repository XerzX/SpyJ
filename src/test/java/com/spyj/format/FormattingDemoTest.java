package com.spyj.format;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class FormattingDemoTest {

    @Test
    void demoWritesSampleJsonAndPdfReports() throws Exception {
        FormattingDemo.main(new String[0]);

        Path json = Path.of("target", "sample-report.json");
        Path pdf = Path.of("target", "sample-report.pdf");

        assertTrue(Files.exists(json));
        assertTrue(Files.size(pdf) > 0);

        JsonObject report = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
        assertEquals("demo-session", report.get("session_id").getAsString());
        assertEquals(60000L, report.get("session_duration").getAsLong());
        assertEquals(3L, report.get("total_compilations").getAsLong());
        assertEquals(1L, report.get("successful_compilations").getAsLong());
        assertEquals(2L, report.get("failed_compilations").getAsLong());
        assertEquals(10, report.getAsJsonArray("events").size());
    }
}
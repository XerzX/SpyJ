package com.spyj.format;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.spyj.logger.EventType;
import com.spyj.logger.TelemetryEvent;

class ExportCommandTest {
    @TempDir
    Path tempDir;

    private final LogFormatter helloFormatter = (events, summary) -> "hello".getBytes(StandardCharsets.UTF_8);

    private ExportCommand commandFor(Path output) {
        return new ExportCommand(helloFormatter, new ArrayList<>(), new HashMap<>(), output);
    }

    @Test
    void writesFormatterOutputToFile() throws IOException {
        Path output = tempDir.resolve("report.txt");

        commandFor(output).execute();

        assertEquals("hello", Files.readString(output));
    }

    @Test
    void createsMissingParentDirectories() throws IOException {
        Path output = tempDir.resolve("a").resolve("b").resolve("report.txt");

        commandFor(output).execute();

        assertTrue(Files.exists(output));
    }

    @Test
    void replacesExistingFileContent() throws IOException {
        Path output = tempDir.resolve("report.txt");
        Files.writeString(output, "old content that is longer than the new content");

        commandFor(output).execute();

        assertEquals("hello", Files.readString(output));
    }

    @Test
    void copiesEventsAndSummaryWhenCreated() throws IOException {
        List<TelemetryEvent> events = new ArrayList<>();
        events.add(new TelemetryEvent(EventType.CLASS_CREATED, new HashMap<>()));
        Map<String, Object> summary = new HashMap<>();
        summary.put("a", 1);

        AtomicInteger eventCount = new AtomicInteger();
        AtomicInteger summaryCount = new AtomicInteger();
        LogFormatter capturing = (e, s) -> {
            eventCount.set(e.size());
            summaryCount.set(s.size());
            return new byte[0];
        };

        ExportCommand command = new ExportCommand(capturing, events, summary, tempDir.resolve("out.bin"));
        events.add(new TelemetryEvent(EventType.CLASS_DELETED, new HashMap<>()));
        summary.put("b", 2);
        command.execute();

        assertEquals(1, eventCount.get());
        assertEquals(1, summaryCount.get());
    }

    @Test
    void nullArgumentsAreRejected() {
        Path output = tempDir.resolve("x.txt");

        assertThrows(NullPointerException.class,
                () -> new ExportCommand(null, new ArrayList<>(), new HashMap<>(), output));
        assertThrows(NullPointerException.class,
                () -> new ExportCommand(helloFormatter, null, new HashMap<>(), output));
        assertThrows(NullPointerException.class,
                () -> new ExportCommand(helloFormatter, new ArrayList<>(), null, output));
        assertThrows(NullPointerException.class,
                () -> new ExportCommand(helloFormatter, new ArrayList<>(), new HashMap<>(), null));
    }

    @Test
    void formatterFailureIsPropagatedAndNoFileIsWritten() {
        Path output = tempDir.resolve("report.txt");
        LogFormatter failing = (events, summary) -> {
            throw new IOException("boom");
        };
        ExportCommand command = new ExportCommand(failing, new ArrayList<>(), new HashMap<>(), output);

        IOException error = assertThrows(IOException.class, command::execute);

        assertEquals("boom", error.getMessage());
        assertFalse(Files.exists(output));
    }
}
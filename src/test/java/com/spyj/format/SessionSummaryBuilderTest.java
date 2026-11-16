package com.spyj.format;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.spyj.logger.DetailsKey;
import com.spyj.logger.EventType;
import com.spyj.logger.StatsTracker;
import com.spyj.logger.TelemetryEvent;

class SessionSummaryBuilderTest {
    private static final Instant START = Instant.parse("2026-11-09T10:00:00Z");
    private static final Instant END = START.plusSeconds(60);

    private SessionSummaryBuilder builder;
    private StatsTracker tracker;

    @BeforeEach
    void setUp() {
        builder = new SessionSummaryBuilder();
        tracker = new StatsTracker();
    }

    private Map<String, Object> details(Object... keysAndValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(((DetailsKey) keysAndValues[i]).label(), keysAndValues[i + 1]);
        }
        return map;
    }

    private void send(EventType type, Map<String, Object> details) {
        tracker.onEvent(new TelemetryEvent(type, details));
    }

    private StatsTracker trackerReturning(Map<String, Object> stats) {
        StatsTracker fake = mock(StatsTracker.class);
        when(fake.getStats()).thenReturn(stats);
        return fake;
    }

    @Test
    void summaryContainsSessionDetails() {
        Map<String, Object> summary = builder.build(tracker, "s1", "placeholder", START, END);

        assertEquals("s1", summary.get("session_id"));
        assertEquals("placeholder", summary.get("student_id"));
        assertEquals("2026-11-09T10:00:00Z", summary.get("start_time"));
        assertEquals("2026-11-09T10:01:00Z", summary.get("end_time"));
        assertEquals(60000L, summary.get("session_duration"));
    }

    @Test
    void summaryCopiesTrackerStatistics() {
        send(EventType.COMPILE_SUCCEEDED, details());
        send(EventType.COMPILE_SUCCEEDED, details());
        send(EventType.COMPILE_FAILED, details());
        send(EventType.EXECUTION_SUCCEEDED, details());
        send(EventType.EXECUTION_EXCEPTION, details());
        send(EventType.EDITOR_BURST, details(DetailsKey.KEYSTROKE_COUNT, 5L, DetailsKey.BURST_DURATION, 6000L));
        send(EventType.EDITOR_IDLE, details(DetailsKey.IDLE_DURATION, 14000L));

        Map<String, Object> summary = builder.build(tracker, "s1", "placeholder", START, END);

        assertEquals(3L, summary.get("total_compilations"));
        assertEquals(2L, summary.get("successful_compilations"));
        assertEquals(1L, summary.get("failed_compilations"));
        assertEquals(2L, summary.get("total_runs"));
        assertEquals(6000L, summary.get("active_typing_time"));
        assertEquals(14000L, summary.get("recorded_editor_idle_time"));
    }

    @Test
    void fullSessionActiveAndIdleTimesAreNullForNow() {
        Map<String, Object> summary = builder.build(tracker, "s1", "placeholder", START, END);

        assertTrue(summary.containsKey("active_time"));
        assertTrue(summary.containsKey("idle_time"));
        assertNull(summary.get("active_time"));
        assertNull(summary.get("idle_time"));
    }

    @Test
    void endBeforeStartIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> builder.build(tracker, "s1", "placeholder", END, START));

        assertEquals("End time cannot be before start time.", error.getMessage());
    }

    @Test
    void equalStartAndEndGiveZeroDuration() {
        Map<String, Object> summary = builder.build(tracker, "s1", "placeholder", START, START);

        assertEquals(0L, summary.get("session_duration"));
    }

    @Test
    void nullTrackerIsRejected() {
        assertThrows(NullPointerException.class,
                () -> builder.build(null, "s1", "placeholder", START, END));
    }

    @Test
    void missingCompilationSectionIsRejected() {
        StatsTracker fake = trackerReturning(new HashMap<>());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> builder.build(fake, "s1", "placeholder", START, END));

        assertTrue(error.getMessage().contains("Compilation"));
    }

    @Test
    void missingExecutionSectionIsRejected() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("Compilation", new HashMap<String, Object>());
        StatsTracker fake = trackerReturning(stats);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> builder.build(fake, "s1", "placeholder", START, END));

        assertTrue(error.getMessage().contains("Execution"));
    }

    @Test
    void missingEditorSectionIsRejected() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("Compilation", new HashMap<String, Object>());
        stats.put("Execution", new HashMap<String, Object>());
        StatsTracker fake = trackerReturning(stats);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> builder.build(fake, "s1", "placeholder", START, END));

        assertTrue(error.getMessage().contains("Editor Interactions"));
    }

    @Test
    void sectionThatIsNotAMapIsRejected() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("Compilation", "not a map");
        StatsTracker fake = trackerReturning(stats);

        assertThrows(IllegalArgumentException.class,
                () -> builder.build(fake, "s1", "placeholder", START, END));
    }
}
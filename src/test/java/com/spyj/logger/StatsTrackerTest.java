package com.spyj.logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class StatsTrackerTest {
    private StatsTracker statsTracker;
    
    @BeforeEach
    void setUp() {
        statsTracker = new StatsTracker();
    }

    private TelemetryEvent createEvent(EventType type, Map<String, Object> details) {
        return new TelemetryEvent(type, details);
    }

    @Test
    void testCompilationStatsMath() {
        statsTracker.onEvent(createEvent(EventType.COMPILE_SUCCEEDED, new HashMap<>()));
        statsTracker.onEvent(createEvent(EventType.COMPILE_SUCCEEDED, new HashMap<>()));
        statsTracker.onEvent(createEvent(EventType.COMPILE_FAILED, new HashMap<>()));
        
        Map<String, Object> stats = statsTracker.getStats();
        Map<String, Object> compilationStats = (Map<String, Object>) stats.get("Compilation");

        assertEquals(3L, compilationStats.get("Total Compiles"));
        assertEquals(66.67, compilationStats.get("Success Rate (%)"));
        assertEquals(2L, compilationStats.get("Succeeded"));
        assertEquals(1L, compilationStats.get("Failed"));
    }

    @Test
    void testEditorIdleTimeMath() {
        Map<String, Object> idle1 = new HashMap<>();
        idle1.put(DetailsKey.IDLE_DURATION.label(), 5000L);
        statsTracker.onEvent(createEvent(EventType.EDITOR_IDLE, idle1));

        Map<String, Object> idle2 = new HashMap<>();
        idle2.put(DetailsKey.IDLE_DURATION.label(), 10000L);
        statsTracker.onEvent(createEvent(EventType.EDITOR_IDLE, idle2));

        Map<String, Object> stats = statsTracker.getStats();
        Map<String, Object> editorStats = (Map<String, Object>) stats.get("Editor Interactions");

        assertEquals(15000L, editorStats.get("Total Idle Time (ms)"));
        assertEquals(10000L, editorStats.get("Longest Idle (ms)"));
        assertEquals(7500.0, editorStats.get("Avg Idle Duration (ms)"));
    }

    @Test
    void testErrorMessageTracking() {
        Map<String, Object> error1 = new HashMap<>();
        error1.put(DetailsKey.ERROR_MESSAGE.label(), "Syntax error on token");
        error1.put(DetailsKey.ERROR_LINE.label(), 10L);

        Map<String, Object> error2 = new HashMap<>();
        error2.put(DetailsKey.ERROR_MESSAGE.label(), "Syntax error on token");
        error2.put(DetailsKey.ERROR_LINE.label(), 10L);

        statsTracker.onEvent(createEvent(EventType.COMPILE_ERROR, error1));
        statsTracker.onEvent(createEvent(EventType.COMPILE_ERROR, error2));

        Map<String, Object> stats = statsTracker.getStats();
        Map<String, Object> errorStats = (Map<String, Object>) stats.get("Compilation");

        assertEquals("Line 10 (2 times)", errorStats.get("Most Frequent Error Line"));
    }
}
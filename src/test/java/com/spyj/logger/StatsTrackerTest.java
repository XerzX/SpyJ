package com.spyj.logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class StatsTrackerTest {
    private StatsTracker statsTracker;

    @BeforeEach
    void setUp() {
        statsTracker = new StatsTracker();
    }

    private TelemetryEvent createEvent(EventType type, Map<String, Object> details) {
        return new TelemetryEvent(type, details);
    }

    private TelemetryEvent eventAt(EventType type, long epochMillis, Map<String, Object> details) {
        TelemetryEvent event = mock(TelemetryEvent.class);
        when(event.getEventType()).thenReturn(type);
        when(event.getTimestamp()).thenReturn(Instant.ofEpochMilli(epochMillis));
        when(event.getEventDetails()).thenReturn(details);
        return event;
    }

    private Map<String, Object> details(Object... keysAndValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(((DetailsKey) keysAndValues[i]).label(), keysAndValues[i + 1]);
        }
        return map;
    }

    private void send(EventType type, Map<String, Object> details) {
        statsTracker.onEvent(createEvent(type, details));
    }

    private void sendError(String message) {
        send(EventType.COMPILE_ERROR, details(DetailsKey.ERROR_MESSAGE, message));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(String name) {
        return (Map<String, Object>) statsTracker.getStats().get(name);
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

    //Empty and invalid input

    @Test
    void testEmptyTrackerReportsZeros() {
        assertEquals(0L, section("Session").get("Total Events"));
        assertEquals(0L, section("Session").get("Duration (ms)"));
        assertEquals(0L, section("Compilation").get("Total Compiles"));
        assertEquals(0.0, section("Compilation").get("Success Rate (%)"));
        assertFalse((Boolean) section("Compilation").get("Unresolved Failure"));
        assertEquals("None", section("Compilation").get("Most Frequent Error Line"));
        assertEquals("None", section("Class Lifecycle").get("Most Modified Class"));
        assertEquals("None", section("Execution").get("Most Run Method"));
        assertEquals(0.0, section("Editor Interactions").get("Typing Time Share (%)"));
    }

    @Test
    void testNullEventsAreIgnored() {
        statsTracker.onEvent(null);
        statsTracker.onEvent(eventAt(null, 1000L, new HashMap<>()));

        assertEquals(0L, section("Session").get("Total Events"));
    }

    @Test
    void testNullDetailsAreHandled() {
        statsTracker.onEvent(eventAt(EventType.COMPILE_ERROR, 1000L, null));

        assertEquals(1L, section("Compilation").get("Errors Reported"));
    }

    @Test
    void testNonNumericDetailValuesCountAsZero() {
        send(EventType.EDITOR_IDLE, details(DetailsKey.IDLE_DURATION, "not a number"));

        assertEquals(1L, section("Editor Interactions").get("Idle Periods"));
        assertEquals(0L, section("Editor Interactions").get("Total Idle Time (ms)"));
    }

    //Session and Editor tests

    @Test
    void testSessionDurationIsLastMinusFirstTimestamp() {
        statsTracker.onEvent(eventAt(EventType.PROJECT_OPENED, 60_000L, new HashMap<>()));
        statsTracker.onEvent(eventAt(EventType.PROJECT_CLOSED, 120_000L, new HashMap<>()));

        assertEquals(2L, section("Session").get("Total Events"));
        assertEquals(60_000L, section("Session").get("First Event"));
        assertEquals(120_000L, section("Session").get("Last Event"));
        assertEquals(60_000L, section("Session").get("Duration (ms)"));
    }

    @Test
    void testBurstStatsAreAccumulated() {
        statsTracker.onEvent(eventAt(EventType.EDITOR_BURST, 60_000L, details(
                DetailsKey.KEYSTROKE_COUNT, 5L,
                DetailsKey.BURST_DURATION, 2000L,
                DetailsKey.CADENCE_KPM, 120L,
                DetailsKey.SESSION_KEYSTROKES, 5L)));
        statsTracker.onEvent(eventAt(EventType.EDITOR_BURST, 120_000L, details(
                DetailsKey.KEYSTROKE_COUNT, 11L,
                DetailsKey.BURST_DURATION, 4000L,
                DetailsKey.CADENCE_KPM, 150L,
                DetailsKey.SESSION_KEYSTROKES, 16L)));

        Map<String, Object> editor = section("Editor Interactions");

        assertEquals(16L, editor.get("Total Keystrokes"));
        assertEquals(16.0, editor.get("Keystrokes Per Minute (Session)"));
        assertEquals(2L, editor.get("Bursts"));
        assertEquals(2.0, editor.get("Bursts Per Minute (Session)"));
        assertEquals(8.0, editor.get("Avg Burst Length (keystrokes)"));
        assertEquals(3000.0, editor.get("Avg Burst Duration (ms)"));
        assertEquals(140.0, editor.get("Avg Cadence (KPM)"));   
        assertEquals(150L, editor.get("Peak Cadence (KPM)"));
        assertEquals(6000L, editor.get("Active Typing Time (ms)"));
    }

    @Test
    void testTypingTimeShareComparesActiveToIdleTime() {
        send(EventType.EDITOR_BURST, details(
                DetailsKey.KEYSTROKE_COUNT, 5L,
                DetailsKey.BURST_DURATION, 6000L));
        send(EventType.EDITOR_IDLE, details(DetailsKey.IDLE_DURATION, 14000L));

        Map<String, Object> editor = section("Editor Interactions");

        assertEquals(6000L, editor.get("Active Typing Time (ms)"));
        assertEquals(14000L, editor.get("Total Idle Time (ms)"));
        assertEquals(30.0, editor.get("Typing Time Share (%)"));   
    }

    //Compilation tests
    
    @Test
    void testFailureThenSuccessRecordsTimeToFix() {
        statsTracker.onEvent(eventAt(EventType.COMPILE_FAILED, 1000L, new HashMap<>()));
        statsTracker.onEvent(eventAt(EventType.COMPILE_SUCCEEDED, 9000L, new HashMap<>()));

        Map<String, Object> compilation = section("Compilation");

        assertEquals(1L, compilation.get("Failures Recovered"));
        assertEquals(8000.0, compilation.get("Avg Time To Fix (ms)"));
        assertFalse((Boolean) compilation.get("Unresolved Failure"));
    }

    @Test
    void testConsecutiveFailuresKeepOriginalFailureTime() {
        statsTracker.onEvent(eventAt(EventType.COMPILE_FAILED, 1000L, new HashMap<>()));
        statsTracker.onEvent(eventAt(EventType.COMPILE_FAILED, 2000L, new HashMap<>()));
        statsTracker.onEvent(eventAt(EventType.COMPILE_SUCCEEDED, 5000L, new HashMap<>()));

        Map<String, Object> compilation = section("Compilation");

        assertEquals(1L, compilation.get("Failures Recovered"));
        assertEquals(4000.0, compilation.get("Avg Time To Fix (ms)"));
    }

    @Test
    void testFailureWithoutFixIsUnresolved() {
        statsTracker.onEvent(eventAt(EventType.COMPILE_FAILED, 1000L, new HashMap<>()));

        Map<String, Object> compilation = section("Compilation");

        assertTrue((Boolean) compilation.get("Unresolved Failure"));
        assertEquals(0L, compilation.get("Failures Recovered"));
        assertEquals(0.0, compilation.get("Avg Time To Fix (ms)"));
    }

    @Test
    void testCompileDurationUserCompilesAndWarnings() {
        send(EventType.COMPILE_SUCCEEDED, details(
                DetailsKey.COMPILE_DURATION, 200L, DetailsKey.USER_GENERATED, true));
        send(EventType.COMPILE_FAILED, details(
                DetailsKey.COMPILE_DURATION, 400L, DetailsKey.USER_GENERATED, false));
        send(EventType.COMPILE_WARNING, details());

        Map<String, Object> compilation = section("Compilation");

        assertEquals(2L, compilation.get("Total Compiles"));
        assertEquals(50.0, compilation.get("Success Rate (%)"));
        assertEquals(300.0, compilation.get("Avg Compile Duration (ms)"));
        assertEquals(1L, compilation.get("User-Initiated Compiles"));
        assertEquals(1L, compilation.get("Warnings Reported"));
    }

    @Test
    void testErrorRatioPerFailedCompile() {
        send(EventType.COMPILE_FAILED, details());
        send(EventType.COMPILE_ERROR, details());
        send(EventType.COMPILE_ERROR, details());
        send(EventType.COMPILE_ERROR, details());

        Map<String, Object> compilation = section("Compilation");

        assertEquals(3L, compilation.get("Errors Reported"));
        assertEquals(3.0, compilation.get("Avg Errors Per Failed Compile"));
        assertEquals(0.0, compilation.get("Success Rate (%)"));
    }

    @Test
    void testErrorMessagesGroupedByFirstLineAndLimitedToTopThree() {
        sendError("cannot find symbol\n  symbol: variable x");
        sendError("cannot find symbol\n  symbol: variable y");
        sendError("cannot find symbol");
        sendError("cannot find symbol\r\n  location: class Foo");
        sendError("';' expected");
        sendError("';' expected");
        sendError("';' expected");
        sendError("incompatible types");
        sendError("incompatible types");
        sendError("missing return statement");

        Map<?, ?> top = (Map<?, ?>) section("Compilation").get("Most Common Errors");

        assertEquals(List.of("cannot find symbol", "';' expected", "incompatible types"),
                new ArrayList<>(top.keySet()));
        assertEquals(4L, top.get("cannot find symbol"));
        assertEquals(3L, top.get("';' expected"));
        assertEquals(2L, top.get("incompatible types"));
        assertNull(top.get("missing return statement"));
    }

    @Test
    void testBlankMessageAndZeroLineAreIgnored() {
        send(EventType.COMPILE_ERROR, details(
                DetailsKey.ERROR_MESSAGE, "   ", DetailsKey.ERROR_LINE, 0L));

        Map<?, ?> top = (Map<?, ?>) section("Compilation").get("Most Common Errors");

        assertTrue(top.isEmpty());
        assertEquals("None", section("Compilation").get("Most Frequent Error Line"));
        assertEquals(1L, section("Compilation").get("Errors Reported"));   
    }

    //Class and Project lifecycle tests

    @Test
    void testClassLifecycleStats() {
        send(EventType.CLASS_CREATED, details(DetailsKey.CLASS_NAME, "Dog"));
        send(EventType.CLASS_MODIFIED, details(DetailsKey.CLASS_NAME, "Dog"));
        send(EventType.CLASS_MODIFIED, details(DetailsKey.CLASS_NAME, "Dog"));
        send(EventType.CLASS_MODIFIED, details(
                DetailsKey.CLASS_NAME, "Cat", DetailsKey.CLASS_OLD_NAME, "Kitten"));
        send(EventType.CLASS_DELETED, details(DetailsKey.CLASS_NAME, "Cat"));

        Map<String, Object> classes = section("Class Lifecycle");

        assertEquals(1L, classes.get("Created"));
        assertEquals(3L, classes.get("Modified"));
        assertEquals(1L, classes.get("Renamed"));
        assertEquals(1L, classes.get("Deleted"));
        assertEquals(2L, classes.get("Distinct Classes Touched"));
        assertEquals("Dog", classes.get("Most Modified Class"));
    }

    @Test
    void testProjectLifecycleStats() {
        send(EventType.PROJECT_OPENED, details(DetailsKey.PROJECT_NAME, "A"));
        send(EventType.PROJECT_OPENED, details(DetailsKey.PROJECT_NAME, "A"));
        send(EventType.PROJECT_OPENED, details(DetailsKey.PROJECT_NAME, "B"));
        send(EventType.PROJECT_CLOSED, details(DetailsKey.PROJECT_NAME, "B"));

        Map<String, Object> projects = section("Project Lifecycle");

        assertEquals(3L, projects.get("Opened"));
        assertEquals(1L, projects.get("Closed"));
        assertEquals(0L, projects.get("Created"));
        assertEquals(2L, projects.get("Distinct Projects Opened"));
    }

    @Test
    void testExecutionStats() {
        send(EventType.EXECUTION_SUCCEEDED, details(
                DetailsKey.CLASS_NAME, "Main", DetailsKey.METHOD_NAME, "main",
                DetailsKey.EXIT_STATUS, "NORMAL_EXIT"));
        send(EventType.EXECUTION_SUCCEEDED, details(
                DetailsKey.CLASS_NAME, "Main", DetailsKey.METHOD_NAME, "main",
                DetailsKey.EXIT_STATUS, "NORMAL_EXIT"));
        send(EventType.EXECUTION_EXCEPTION, details(
                DetailsKey.CLASS_NAME, "Calc", DetailsKey.METHOD_NAME, "divide",
                DetailsKey.EXIT_STATUS, "EXCEPTION_EXIT"));
        send(EventType.EXECUTION_SUCCEEDED, details(DetailsKey.CLASS_NAME, "Calc"));

        Map<String, Object> execution = section("Execution");
        Map<?, ?> exitStatuses = (Map<?, ?>) execution.get("Exit Status Breakdown");

        assertEquals(4L, execution.get("Total Runs"));
        assertEquals(3L, execution.get("Succeeded"));
        assertEquals(1L, execution.get("Ended With Exception"));
        assertEquals(25.0, execution.get("Exception Rate (%)"));
        assertEquals(3L, execution.get("Distinct Methods Run"));  
        assertEquals("Main.main", execution.get("Most Run Method"));
        assertEquals(2L, exitStatuses.get("NORMAL_EXIT"));
        assertEquals(1L, exitStatuses.get("EXCEPTION_EXIT"));
    }

    //Event counts and reset tests

    @Test
    void testEventCountsListEveryEventType() {
        send(EventType.CLASS_CREATED, details());

        Map<String, Object> counts = section("Event Counts");

        assertEquals(EventType.values().length, counts.size());
        assertEquals(1L, counts.get("CLASS_CREATED"));
        assertEquals(0L, counts.get("PROJECT_OPENED"));
    }

    @Test
    void testResetClearsAllStats() {
        send(EventType.COMPILE_FAILED, details());
        send(EventType.CLASS_MODIFIED, details(DetailsKey.CLASS_NAME, "Dog"));
        send(EventType.EDITOR_IDLE, details(DetailsKey.IDLE_DURATION, 5000L));

        statsTracker.reset();

        assertEquals(0L, section("Session").get("Total Events"));
        assertFalse((Boolean) section("Compilation").get("Unresolved Failure"));
        assertEquals("None", section("Class Lifecycle").get("Most Modified Class"));
        assertEquals(0L, section("Editor Interactions").get("Total Idle Time (ms)"));
    }
}
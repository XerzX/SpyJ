package com.spyj.logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An {@link EventSubscriber} that maintains a continuously-updated set of aggregated
 * statistics across all telemetry event categories.
 * Counters are updated incrementally as each event arrives, ensuring an up-to-date
 * snapshot is always available via {@link #getStats()}.
 *
 * <p>All public methods are {@code synchronized} to allow safe concurrent access from
 * both the BlueJ event thread and the JavaFX Application Thread used by EditorListener.</p>
 *
 * @author Mykel Dedier
 */
public class StatsTracker implements EventSubscriber {

    /** The maximum number of top compilation error messages to include in the stats map. */
    private static final int TOP_ERROR_COUNT = 3;

    /** Count of occurrences for each {@link EventType} seen during the session. */
    private final HashMap<EventType, Long> eventCounts;

    /** Frequency map of compiler error message texts (first line only). */
    private final HashMap<String, Long> errorMessageCounts;

    /** Frequency map of source-code line numbers where compiler errors were reported. */
    private final HashMap<String, Long> errorLineCounts;

    /** Per-class modification counts, keyed by class name. */
    private final HashMap<String, Long> classModifyCounts;

    /** Count of invocations grouped by their BlueJ exit-status string. */
    private final HashMap<String, Long> exitStatusCounts;

    /** Count of invocations grouped by {@code "ClassName.methodName"}. */
    private final HashMap<String, Long> methodRunCounts;

    /** Set of distinct class names touched during the session. */
    private final HashSet<String> classesTouched;

    /** Set of distinct project names opened during the session. */
    private final HashSet<String> projectsOpened;

    /** Total number of events received. */
    private long totalEvents;

    /** Epoch-millisecond timestamp of the first event, or {@code -1} if none yet. */
    private long firstEventTime;

    /** Epoch-millisecond timestamp of the most recently seen event. */
    private long lastEventTime;

    /** Total number of class rename operations recorded. */
    private long classRenames;
    /** Cumulative compilation duration across all compile attempts, in milliseconds. */
    private long compileTimeMs;

    /** Number of compilations triggered directly by the user. */
    private long userCompiles;

    /**
     * Epoch-millisecond timestamp of the first failed compile in the current failure streak,
     * or {@code -1} if no unresolved failure exists.
     */
    private long failedSince;

    /** Cumulative time in milliseconds spent recovering from compilation failures. */
    private long recoveryTimeMs;

    /** Number of times the user recovered from a compilation failure streak. */
    private long recoveries;

    /** Total keystrokes accumulated across all recorded bursts. */
    private long burstKeystrokes;

    /** Total number of inter-keystroke intervals across all bursts. */
    private long burstIntervals;

    /** Total active typing time in milliseconds across all bursts. */
    private long burstTimeMs;

    /** Highest keystroke-per-minute cadence recorded across all bursts. */
    private long peakKpm;

    /** Running session-wide keystroke total, sourced from EditorListener. */
    private long sessionKeystrokes;

    /** Cumulative idle time in milliseconds across all idle periods. */
    private long idleTimeMs;

    /** Duration in milliseconds of the longest single idle period. */
    private long longestIdleMs;

    /**
     * Creates a new StatsTracker and initialises all counters to their zero state.
     */
    public StatsTracker() {
        this.eventCounts = new HashMap<>();
        this.errorMessageCounts = new HashMap<>();
        this.errorLineCounts = new HashMap<>();
        this.classModifyCounts = new HashMap<>();
        this.exitStatusCounts = new HashMap<>();
        this.methodRunCounts = new HashMap<>();
        this.classesTouched = new HashSet<>();
        this.projectsOpened = new HashSet<>();

        reset();
    }

    /**
     * Resets all counters and clears all frequency maps and sets.
     * After calling this method the tracker is in the same state as a freshly constructed instance.
     */
    public synchronized void reset() {
        eventCounts.clear();
        errorMessageCounts.clear();
        errorLineCounts.clear();
        classModifyCounts.clear();
        exitStatusCounts.clear();
        methodRunCounts.clear();
        classesTouched.clear();
        projectsOpened.clear();

        totalEvents = 0;
        firstEventTime = -1;
        lastEventTime = 0;

        classRenames = 0;

        compileTimeMs = 0;
        userCompiles = 0;
        failedSince = -1;
        recoveryTimeMs = 0;
        recoveries = 0;

        burstKeystrokes = 0;
        burstIntervals = 0;
        burstTimeMs = 0;
        peakKpm = 0;
        sessionKeystrokes = 0;
        idleTimeMs = 0;
        longestIdleMs = 0;
    }

    /**
     * Processes a telemetry event and updates the relevant statistics.
     * Null events or events with a null type are silently ignored.
     *
     * @param event the telemetry event to process
     */
    @Override
    public synchronized void onEvent(TelemetryEvent event) {
        if (event == null || event.getEventType() == null) {
            return;
        }

        EventType type = event.getEventType();
        long timestamp = event.getTimestamp().toEpochMilli();
        Map<String, Object> details = event.getEventDetails() != null
                ? event.getEventDetails()
                : Collections.emptyMap();

        totalEvents++;
        eventCounts.put(type, count(type) + 1);

        if (firstEventTime < 0) {
            firstEventTime = timestamp;
        }
        lastEventTime = Math.max(lastEventTime, timestamp);

        switch (type) {
            case PROJECT_OPENED:
                onProjectOpened(details);
                break;
            case CLASS_CREATED:
            case CLASS_DELETED:
                onClassEvent(details, false);
                break;
            case CLASS_MODIFIED:
                onClassEvent(details, true);
                break;
            case EDITOR_BURST:
                onEditorBurst(details);
                break;
            case EDITOR_IDLE:
                onEditorIdle(details);
                break;
            case COMPILE_SUCCEEDED:
                onCompileFinished(timestamp, details, true);
                break;
            case COMPILE_FAILED:
                onCompileFinished(timestamp, details, false);
                break;
            case COMPILE_ERROR:
                onCompileError(details);
                break;
            case EXECUTION_SUCCEEDED:
            case EXECUTION_EXCEPTION:
                onExecution(details);
                break;
            default:
                break;
        }
    }

    /**
     * Records the project name from a {@code PROJECT_OPENED} event into the distinct-projects set.
     *
     * @param details the event detail map
     */
    private void onProjectOpened(Map<String, Object> details) {
        Object name = details.get(DetailsKey.PROJECT_NAME.label());
        if (name != null) {
            projectsOpened.add(name.toString());
        }
    }

    /**
     * Updates class lifecycle statistics for {@code CLASS_CREATED}, {@code CLASS_DELETED},
     * and {@code CLASS_MODIFIED} events. Tracks renames, the touched-class set, and
     * per-class modification counts.
     *
     * @param details  the event detail map
     * @param modified {@code true} when the event represents a modification or rename;
     *                 {@code false} for creation or deletion
     */
    private void onClassEvent(Map<String, Object> details, boolean modified) {
        if (modified && details.get(DetailsKey.CLASS_OLD_NAME.label()) != null) {
            classRenames++;
        }

        Object name = details.get(DetailsKey.CLASS_NAME.label());
        if (name != null) {
            classesTouched.add(name.toString());
            if (modified) {
                increment(classModifyCounts, name.toString());
            }
        }
    }

    /**
     * Accumulates keystroke count, duration, and cadence statistics from an {@code EDITOR_BURST} event.
     *
     * @param details the event detail map
     */
    private void onEditorBurst(Map<String, Object> details) {
        long keystrokes = asLong(details.get(DetailsKey.KEYSTROKE_COUNT.label()));

        burstKeystrokes += keystrokes;
        burstIntervals += Math.max(keystrokes - 1, 0);
        burstTimeMs += asLong(details.get(DetailsKey.BURST_DURATION.label()));
        peakKpm = Math.max(peakKpm, asLong(details.get(DetailsKey.CADENCE_KPM.label())));
        sessionKeystrokes = Math.max(sessionKeystrokes, asLong(details.get(DetailsKey.SESSION_KEYSTROKES.label())));
    }

    /**
     * Accumulates idle time and updates the longest-idle record from an {@code EDITOR_IDLE} event.
     *
     * @param details the event detail map
     */
    private void onEditorIdle(Map<String, Object> details) {
        long idle = asLong(details.get(DetailsKey.IDLE_DURATION.label()));

        idleTimeMs += idle;
        longestIdleMs = Math.max(longestIdleMs, idle);
    }

    /**
     * Updates compilation statistics from a {@code COMPILE_SUCCEEDED} or {@code COMPILE_FAILED} event.
     * Tracks compile duration, user-initiated flag, and failure-recovery timing.
     *
     * @param timestamp the epoch-millisecond timestamp of this event
     * @param details   the event detail map
     * @param succeeded {@code true} if the compilation succeeded; {@code false} if it failed
     */
    private void onCompileFinished(long timestamp, Map<String, Object> details, boolean succeeded) {
        compileTimeMs += asLong(details.get(DetailsKey.COMPILE_DURATION.label()));

        if (Boolean.TRUE.equals(details.get(DetailsKey.USER_GENERATED.label()))) {
            userCompiles++;
        }

        if (succeeded) {
            if (failedSince >= 0) {
                recoveryTimeMs += timestamp - failedSince;
                recoveries++;
                failedSince = -1;
            }
        } else if (failedSince < 0) {
            failedSince = timestamp;
        }
    }

    /**
     * Records the error message and line number from a {@code COMPILE_ERROR} event.
     * Only the first line of multi-line compiler messages is used as the frequency key.
     *
     * @param details the event detail map
     */
    private void onCompileError(Map<String, Object> details) {
        Object message = details.get(DetailsKey.ERROR_MESSAGE.label());
        if (message != null) {
            // First line only, so multi-line javac messages group together
            String key = message.toString().split("\\R", 2)[0].trim();
            if (!key.isEmpty()) {
                increment(errorMessageCounts, key);
            }
        }

        long line = asLong(details.get(DetailsKey.ERROR_LINE.label()));
        if (line > 0) {
            increment(errorLineCounts, String.valueOf(line));
        }
    }

    /**
     * Records exit status and per-method run counts from an execution event.
     *
     * @param details the event detail map
     */
    private void onExecution(Map<String, Object> details) {
        Object status = details.get(DetailsKey.EXIT_STATUS.label());
        if (status != null) {
            increment(exitStatusCounts, status.toString());
        }

        Object className = details.get(DetailsKey.CLASS_NAME.label());
        if (className != null) {
            Object method = details.get(DetailsKey.METHOD_NAME.label());
            String methodName = method != null ? method.toString() : "<init>";
            increment(methodRunCounts, className + "." + methodName);
        }
    }

    /**
     * Returns a snapshot of all aggregated statistics, grouped by category.
     * The returned map contains the following top-level keys, each mapping to a nested stats map:
     * {@code Session}, {@code Project Lifecycle}, {@code Class Lifecycle},
     * {@code Editor Interactions}, {@code Compilation}, {@code Execution}, {@code Event Counts}.
     *
     * @return a map of category names to their respective statistics maps
     */
    public synchronized Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("Session", sessionStats());
        stats.put("Project Lifecycle", projectStats());
        stats.put("Class Lifecycle", classStats());
        stats.put("Editor Interactions", editorStats());
        stats.put("Compilation", compilationStats());
        stats.put("Execution", executionStats());
        stats.put("Event Counts", eventCountStats());

        return stats;
    }

    /**
     * Builds the session-level statistics sub-map.
     *
     * @return a map containing total events, first/last event timestamps, and session duration
     */
    private Map<String, Object> sessionStats() {
        Map<String, Object> session = new LinkedHashMap<>();

        session.put("Total Events", totalEvents);
        session.put("First Event", firstEventTime < 0 ? 0L : firstEventTime);
        session.put("Last Event", lastEventTime);
        session.put("Duration (ms)", firstEventTime < 0 ? 0L : lastEventTime - firstEventTime);

        return session;
    }

    /**
     * Builds the project lifecycle statistics sub-map.
     *
     * @return a map of project event counts and the number of distinct projects opened
     */
    private Map<String, Object> projectStats() {
        Map<String, Object> projects = new LinkedHashMap<>();

        projects.put("Created", count(EventType.PROJECT_CREATED));
        projects.put("Opened", count(EventType.PROJECT_OPENED));
        projects.put("Closed", count(EventType.PROJECT_CLOSED));
        projects.put("Modified", count(EventType.PROJECT_MODIFIED));
        projects.put("Deleted", count(EventType.PROJECT_DELETED));
        projects.put("Distinct Projects Opened", (long) projectsOpened.size());

        return projects;
    }

    /**
     * Builds the class lifecycle statistics sub-map.
     *
     * @return a map of class event counts, rename count, distinct classes touched,
     *         and the most-modified class name
     */
    private Map<String, Object> classStats() {
        String mostModified = mostFrequent(classModifyCounts);

        Map<String, Object> classes = new LinkedHashMap<>();

        classes.put("Created", count(EventType.CLASS_CREATED));
        classes.put("Modified", count(EventType.CLASS_MODIFIED));
        classes.put("Renamed", classRenames);
        classes.put("Deleted", count(EventType.CLASS_DELETED));
        classes.put("Distinct Classes Touched", (long) classesTouched.size());
        classes.put("Most Modified Class", mostModified != null ? mostModified : "None");

        return classes;
    }

    /**
     * Builds the editor interaction statistics sub-map.
     *
     * @return a map of keystroke, burst, cadence, and idle metrics
     */
    private Map<String, Object> editorStats() {
        long bursts = count(EventType.EDITOR_BURST);
        long idlePeriods = count(EventType.EDITOR_IDLE);
        long totalKeystrokes = Math.max(sessionKeystrokes, burstKeystrokes);
        double burstMinutes = burstTimeMs / 60000.0;
        double sessionMinutes = firstEventTime < 0 ? 0 : (lastEventTime - firstEventTime) / 60000.0;

        Map<String, Object> editor = new LinkedHashMap<>();

        editor.put("Total Keystrokes", totalKeystrokes);
        editor.put("Keystrokes Per Minute (Session)", ratio(totalKeystrokes, sessionMinutes));
        editor.put("Bursts", bursts);
        editor.put("Bursts Per Minute (Session)", ratio(bursts, sessionMinutes));
        editor.put("Avg Burst Length (keystrokes)", ratio(burstKeystrokes, bursts));
        editor.put("Avg Burst Duration (ms)", ratio(burstTimeMs, bursts));
        editor.put("Avg Cadence (KPM)", ratio(burstIntervals, burstMinutes));
        editor.put("Peak Cadence (KPM)", peakKpm);
        editor.put("Active Typing Time (ms)", burstTimeMs);
        editor.put("Idle Periods", idlePeriods);
        editor.put("Total Idle Time (ms)", idleTimeMs);
        editor.put("Avg Idle Duration (ms)", ratio(idleTimeMs, idlePeriods));
        editor.put("Longest Idle (ms)", longestIdleMs);
        editor.put("Typing Time Share (%)", ratio(burstTimeMs * 100.0, burstTimeMs + idleTimeMs));

        return editor;
    }

    /**
     * Builds the compilation statistics sub-map.
     *
     * @return a map of compile counts, success rate, error frequencies, duration averages,
     *         and failure-recovery metrics
     */
    private Map<String, Object> compilationStats() {
        long succeeded = count(EventType.COMPILE_SUCCEEDED);
        long failed = count(EventType.COMPILE_FAILED);
        long compiles = succeeded + failed;
        long errors = count(EventType.COMPILE_ERROR);
        String worstLine = mostFrequent(errorLineCounts);

        Map<String, Object> compilation = new LinkedHashMap<>();

        compilation.put("Total Compiles", compiles);
        compilation.put("Succeeded", succeeded);
        compilation.put("Failed", failed);
        compilation.put("Success Rate (%)", ratio(succeeded * 100.0, compiles));
        compilation.put("User-Initiated Compiles", userCompiles);
        compilation.put("Errors Reported", errors);
        compilation.put("Warnings Reported", count(EventType.COMPILE_WARNING));
        compilation.put("Avg Errors Per Failed Compile", ratio(errors, failed));
        compilation.put("Avg Compile Duration (ms)", ratio(compileTimeMs, compiles));
        compilation.put("Failures Recovered", recoveries);
        compilation.put("Avg Time To Fix (ms)", ratio(recoveryTimeMs, recoveries));
        compilation.put("Unresolved Failure", failedSince >= 0);
        compilation.put("Most Common Errors", topErrors());
        compilation.put("Most Frequent Error Line", worstLine != null
                ? "Line " + worstLine + " (" + errorLineCounts.get(worstLine) + " times)"
                : "None");

        return compilation;
    }

    /**
     * Builds the execution statistics sub-map.
     *
     * @return a map of run counts, exception rate, exit-status breakdown,
     *         and the most-invoked method
     */
    private Map<String, Object> executionStats() {
        long succeeded = count(EventType.EXECUTION_SUCCEEDED);
        long exceptions = count(EventType.EXECUTION_EXCEPTION);
        long runs = succeeded + exceptions;
        String mostRun = mostFrequent(methodRunCounts);

        Map<String, Object> execution = new LinkedHashMap<>();

        execution.put("Total Runs", runs);
        execution.put("Succeeded", succeeded);
        execution.put("Ended With Exception", exceptions);
        execution.put("Exception Rate (%)", ratio(exceptions * 100.0, runs));
        execution.put("Exit Status Breakdown", new LinkedHashMap<String, Object>(exitStatusCounts));
        execution.put("Distinct Methods Run", (long) methodRunCounts.size());
        execution.put("Most Run Method", mostRun != null ? mostRun : "None");

        return execution;
    }

    /**
     * Builds an event-count sub-map listing the number of occurrences of each {@link EventType}.
     * Event types appear in their declaration order, keeping the report layout consistent.
     *
     * @return a map of event type names to their occurrence counts
     */
    private Map<String, Object> eventCountStats() {
        Map<String, Object> counts = new LinkedHashMap<>();

        // values() gives declaration order, so the report layout never changes
        for (EventType type : EventType.values()) {
            counts.put(type.name(), count(type));
        }

        return counts;
    }

    /**
     * Builds an ordered map of the top {@link #TOP_ERROR_COUNT} most frequent compiler error messages.
     *
     * @return a map of error message strings to their occurrence counts, highest first
     */
    private Map<String, Object> topErrors() {
        Map<String, Object> top = new LinkedHashMap<>();
        HashMap<String, Long> remaining = new HashMap<>(errorMessageCounts);

        for (int i = 0; i < TOP_ERROR_COUNT; i++) {
            String best = mostFrequent(remaining);
            if (best == null) {
                break;
            }
            top.put(best, remaining.remove(best));
        }

        return top;
    }

    /**
     * Returns the key with the highest count from the given frequency map,
     * or {@code null} if the map is empty.
     *
     * @param counts the frequency map to search
     * @return the most frequent key, or {@code null} if the map is empty
     */
    private String mostFrequent(HashMap<String, Long> counts) {
        String best = null;
        long bestCount = 0;

        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }

        return best;
    }

    /**
     * Increments the count for the given key in a frequency map by one.
     *
     * @param counts the frequency map to update
     * @param key    the key whose count should be incremented
     */
    private void increment(HashMap<String, Long> counts, String key) {
        counts.put(key, counts.getOrDefault(key, 0L) + 1);
    }

    /**
     * Returns the recorded count for the given event type, or {@code 0} if none have been seen.
     *
     * @param type the event type to look up
     * @return the number of events of the given type recorded so far
     */
    private long count(EventType type) {
        return eventCounts.getOrDefault(type, 0L);
    }

    /**
     * Safely extracts a {@code long} value from an {@link Object}.
     * Returns {@code 0} if the value is not a {@link Number}.
     *
     * @param value the object to convert
     * @return the long value, or {@code 0} if the object is not a Number
     */
    private long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    /**
     * Divides {@code numerator} by {@code denominator}, rounds to two decimal places,
     * and returns {@code 0.0} if the denominator is zero.
     *
     * @param numerator   the dividend
     * @param denominator the divisor
     * @return the rounded ratio, or {@code 0.0} if the denominator is zero
     */
    private double ratio(double numerator, double denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return Math.round(numerator / denominator * 100.0) / 100.0;
    }
}

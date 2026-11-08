package com.spyj.logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

public class StatsTracker implements EventSubscriber {

    private static final int TOP_ERROR_COUNT = 3;

    private final HashMap<EventType, Long> eventCounts;
    private final HashMap<String, Long> errorMessageCounts;
    private final HashMap<String, Long> errorLineCounts;
    private final HashMap<String, Long> classModifyCounts;
    private final HashMap<String, Long> exitStatusCounts;
    private final HashMap<String, Long> methodRunCounts;
    private final HashSet<String> classesTouched;
    private final HashSet<String> projectsOpened;

    private long totalEvents;
    private long firstEventTime;
    private long lastEventTime;

    private long classRenames;

    private long compileTimeMs;
    private long userCompiles;
    private long failedSince;
    private long recoveryTimeMs;
    private long recoveries;

    private long burstKeystrokes;
    private long burstIntervals;
    private long burstTimeMs;
    private long peakKpm;
    private long sessionKeystrokes;
    private long idleTimeMs;
    private long longestIdleMs;

    public StatsTracker () {
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

    public synchronized void reset () {
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

    @Override
    public synchronized void onEvent (TelemetryEvent event) {
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

    private void onProjectOpened (Map<String, Object> details) {
        Object name = details.get(DetailsKey.PROJECT_NAME.label());
        if (name != null) {
            projectsOpened.add(name.toString());
        }
    }

    private void onClassEvent (Map<String, Object> details, boolean modified) {
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

    private void onEditorBurst (Map<String, Object> details) {
        long keystrokes = asLong(details.get(DetailsKey.KEYSTROKE_COUNT.label()));

        burstKeystrokes += keystrokes;
        burstIntervals += Math.max(keystrokes - 1, 0);
        burstTimeMs += asLong(details.get(DetailsKey.BURST_DURATION.label()));
        peakKpm = Math.max(peakKpm, asLong(details.get(DetailsKey.CADENCE_KPM.label())));
        sessionKeystrokes = Math.max(sessionKeystrokes, asLong(details.get(DetailsKey.SESSION_KEYSTROKES.label())));
    }

    private void onEditorIdle (Map<String, Object> details) {
        long idle = asLong(details.get(DetailsKey.IDLE_DURATION.label()));

        idleTimeMs += idle;
        longestIdleMs = Math.max(longestIdleMs, idle);
    }

    private void onCompileFinished (long timestamp, Map<String, Object> details, boolean succeeded) {
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

    private void onCompileError (Map<String, Object> details) {
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

    private void onExecution (Map<String, Object> details) {
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

    public synchronized Map<String, Object> getStats () {
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

    private Map<String, Object> sessionStats () {
        Map<String, Object> session = new LinkedHashMap<>();

        session.put("Total Events", totalEvents);
        session.put("First Event", firstEventTime < 0 ? 0L : firstEventTime);
        session.put("Last Event", lastEventTime);
        session.put("Duration (ms)", firstEventTime < 0 ? 0L : lastEventTime - firstEventTime);

        return session;
    }

    private Map<String, Object> projectStats () {
        Map<String, Object> projects = new LinkedHashMap<>();

        projects.put("Created", count(EventType.PROJECT_CREATED));
        projects.put("Opened", count(EventType.PROJECT_OPENED));
        projects.put("Closed", count(EventType.PROJECT_CLOSED));
        projects.put("Modified", count(EventType.PROJECT_MODIFIED));
        projects.put("Deleted", count(EventType.PROJECT_DELETED));
        projects.put("Distinct Projects Opened", (long) projectsOpened.size());

        return projects;
    }

    private Map<String, Object> classStats () {
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

    private Map<String, Object> editorStats () {
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

    private Map<String, Object> compilationStats () {
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

    private Map<String, Object> executionStats () {
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

    private Map<String, Object> eventCountStats () {
        Map<String, Object> counts = new LinkedHashMap<>();

        // values() gives declaration order, so the report layout never changes
        for (EventType type : EventType.values()) {
            counts.put(type.name(), count(type));
        }

        return counts;
    }

    private Map<String, Object> topErrors () {
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

    // Key with the highest count, or null if the map is empty
    private String mostFrequent (HashMap<String, Long> counts) {
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

    private void increment (HashMap<String, Long> counts, String key) {
        counts.put(key, counts.getOrDefault(key, 0L) + 1);
    }

    private long count (EventType type) {
        return eventCounts.getOrDefault(type, 0L);
    }

    private long asLong (Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private double ratio (double numerator, double denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return Math.round(numerator / denominator * 100.0) / 100.0;
    }
}

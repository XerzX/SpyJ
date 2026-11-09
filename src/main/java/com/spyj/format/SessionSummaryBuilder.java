package com.spyj.format;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.spyj.logger.StatsTracker;

// Prepares session information for the reports.
public class SessionSummaryBuilder {
    //Converts tracked statistics into the field names used by our reports
    public Map<String, Object> build(StatsTracker tracker,String sessionId, String studentId, Instant start, Instant end) {

        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End time cannot be before start time.");
        }

        // Gets one copy of the current statistics.
        Map<String, Object> stats = tracker.getStats();
        //Statistic stored in separate groups
        Map<?, ?> compilation = getSection(stats, "Compilation");
        Map<?, ?> execution = getSection(stats, "Execution");
        Map<?, ?> editor = getSection(stats, "Editor Interactions");

        Map<String, Object> summary = new LinkedHashMap<>();

        // Stores the session details.
        summary.put("session_id", sessionId);
        summary.put("student_id", studentId);
        //Converts time stamps to text
        summary.put("start_time", start.toString());
        summary.put("end_time", end.toString());
        summary.put("session_duration",Duration.between(start, end).toMillis());

        // Full-session activity measurements are not available yet.
        summary.put("active_time", null);
        summary.put("idle_time", null);

        // Keeps the editor measurements under specific names.
        summary.put("active_typing_time",editor.get("Active Typing Time (ms)"));
        summary.put( "recorded_editor_idle_time", editor.get("Total Idle Time (ms)"));

        // Stores the compilation counts.
        summary.put("total_compilations",compilation.get("Total Compiles"));
        summary.put("successful_compilations",compilation.get("Succeeded"));
        summary.put("failed_compilations",compilation.get("Failed"));

        // Stores the execution count.
        summary.put("total_runs", execution.get("Total Runs"));

        return summary;
    }

    // Gets a group of statistics.
    private Map<?, ?> getSection(
            Map<String, Object> stats,
            String name) {

        Object section = stats.get(name);

        if (section instanceof Map<?, ?> map) {
            return map;
        }
        //Reports a missing or in correctly structured group
        throw new IllegalArgumentException("Missing statistics section: " + name);
    }
}

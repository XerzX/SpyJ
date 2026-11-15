package com.spyj.format;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.spyj.logger.TelemetryEvent;

/**
 * Defines a common interface for formatting telemetry reports.
 * JSONFormatter and PDFFormatter provide different strategies.
 *
 * @author Caitlyn Maharaj
 */
public interface LogFormatter {

    /**
     * Converts recorded events and session information into report bytes.
     *
     * @param events the recorded telemetry events
     * @param sessionSummary the session information and statistics
     * @return the formatted report as bytes
     * @throws IOException if an input/output error occurs during formatting
     */
    byte[] format(
            List<TelemetryEvent> events,
            Map<String, Object> sessionSummary) throws IOException;
}

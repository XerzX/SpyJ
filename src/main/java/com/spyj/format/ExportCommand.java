package com.spyj.format;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.spyj.logger.TelemetryEvent;

/**
 * Formats telemetry data and saves it to a report file.
 * Acts as a concrete command in the Command pattern.
 *
 * @author Caitlyn Maharaj
 */
public class ExportCommand implements Command {

    /** Strategy used to generate the report content. */
    private final LogFormatter formatter;

    /** Shallow copy of the telemetry event list to export. */
    private final List<TelemetryEvent> events;

    /** Shallow copy of the session information and statistics. */
    private final Map<String, Object> summary;

    /** Destination path for the generated report file. */
    private final Path output;

    /**
     * Creates a command containing the information needed for one export.
     * Copies the event list and summary map, but not their nested objects.
     *
     * @param formatter the strategy used to format the report
     * @param events the recorded telemetry events
     * @param summary the session information and statistics
     * @param output the destination file path
     * @throws NullPointerException if any argument is null
     */
    public ExportCommand(
            LogFormatter formatter,
            List<TelemetryEvent> events,
            Map<String, Object> summary,
            Path output) {

        this.formatter = Objects.requireNonNull(formatter);
        this.output = Objects.requireNonNull(output);
        this.events = new ArrayList<>(events);
        this.summary = new LinkedHashMap<>(summary);
    }

    /**
     * Generates the report and writes it to the destination file.
     * Creates missing parent directories and replaces existing file content.
     *
     * @throws IOException if report generation, directory creation,
     *                     or file writing fails
     */
    @Override
    public void execute() throws IOException {
        // Converts the data into report bytes.
        byte[] content = formatter.format(events, summary);

        // Creates the destination folder if necessary.
        Path parent = output.toAbsolutePath().getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        // Saves the report, replacing existing content.
        Files.write(output, content);
    }
}

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

    private final LogFormatter formatter;
    private final List<TelemetryEvent> events;
    private final Map<String,Object>summary;
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
    public ExportCommand(LogFormatter formatter,List<TelemetryEvent>events, Map<String,Object>summary,Path output){
        this.formatter=Objects.requireNonNull(formatter);
        this.output=Objects.requireNonNull(output);
        this.events=new ArrayList<>(events);
        this.summary=new LinkedHashMap<>(summary); 
    }
    @Override
    public void execute() throws IOException{
        //Converts the data into JSON or PDF bytes
        byte[]content=formatter.format(events,summary);

        //Creates a destination folder
        Path parent= output.toAbsolutePath().getParent();

        if( parent!=null){
            Files.createDirectories(parent);
        }

        //Saves the report, replacing an existing file path
        Files.write(output,content);
    }
}


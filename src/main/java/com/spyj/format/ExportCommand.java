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

// Saves a report using the selected formatter.
public class ExportCommand implements Command {

    private final LogFormatter formatter;
    private final List<TelemetryEvent> events;
    private final Map<String,Object>summary;
    private final Path output;

    //Receives what is needed to export one report
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


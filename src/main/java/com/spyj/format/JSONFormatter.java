package com.spyj.format;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.spyj.logger.TelemetryEvent;

//Formats telemetry data as JSON using the startegy pattern
public class JSONFormatter implements LogFormatter {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    @Override 
    public byte[] format(List<TelemetryEvent>events, Map<String,Object> sessionSummary) throws IOException{
        //Copies the session information
        Map<String, Object> report =new LinkedHashMap<>(sessionSummary);

        //Stores the formatted events
        List<Map<String,Object>>formattedEvents = new ArrayList<>();

        for(TelemetryEvent event: events){
            Map<String,Object> eventData = new LinkedHashMap<>();

            //Stores timestamp as text
            eventData.put("timestamp", event.getTimestamp().toString());
            eventData.put("type",event.getEventType().name());
            eventData.put("details",event.getEventDetails());
            formattedEvents.add(eventData);
        }

        //Add events to the report
        report.put("events", formattedEvents);
        String json=gson.toJson(report);
        return json.getBytes(StandardCharsets.UTF_8);
        }
}
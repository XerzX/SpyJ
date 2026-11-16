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

/**
 * Converts session information and recorded events into JSON.
 * Includes each event's timestamp, type, and details.
 * Uses the Strategy pattern through LogFormatter.
 *
 * @author Caitlyn Maharaj
 */
public class JSONFormatter implements LogFormatter {
/**
 * Serialises report data as readable JSON and includes null values.
 */
private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
/**
 * Creates a readable JSON report as UTF-8 bytes.
 *
 * @param events the recorded telemetry events
 * @param sessionSummary the session information and statistics
 * @return the formatted JSON report as bytes
 */
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

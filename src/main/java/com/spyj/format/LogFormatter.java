package com.spyj.format;

import java.io.IOException;
import java.util.List;
import java.util.Map;

//Strategy interface usd by the JSON nad PDF formatters
import com.spyj.logger.TelemetryEvent;

public interface LogFormatter {
    //Receives the recorded events and session informatioon
    byte[] format( List<TelemetryEvent> events,Map<String,Object>sessionSummary) throws IOException;
    
}


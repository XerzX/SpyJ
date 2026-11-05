package com.spyj.logger;

public interface EventSubscriber {
    public void onEvent(TelemetryEvent e);
}

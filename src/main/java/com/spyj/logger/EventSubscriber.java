package com.spyj.logger;

/**
 * Defines the contract for receiving telemetry events from {@link SpyJLogger}.
 * EventStore and StatsTracker implement this interface to process captured events.
 * Follows the Observer pattern as the subscriber role.
 *
 * @author Mykel Dedier
 */
public interface EventSubscriber {

    /**
     * Called by {@link SpyJLogger} when a new {@link TelemetryEvent} has been recorded.
     * Implementations should handle exceptions internally to avoid disrupting other subscribers.
     *
     * @param e the telemetry event that was recorded
     */
    public void onEvent(TelemetryEvent e);
}

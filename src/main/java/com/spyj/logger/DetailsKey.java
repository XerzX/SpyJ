package com.spyj.logger;

/**
 * Enumerates the standardised keys used in {@link TelemetryEvent} detail maps.
 * Using an enum instead of raw strings prevents typos and makes it straightforward
 * to find every location where a particular detail is written or read.
 * Each constant wraps a human-readable label string that is stored in the event map.
 *
 * @author Mykel Dedier
 */
public enum DetailsKey {

    /** The name of the BlueJ project. Used by ProjectListener and StatsTracker. */
    PROJECT_NAME("Project"),

    /** The name of the class. Used by ClassListener, ExecutionListener, and StatsTracker. */
    CLASS_NAME("Class"),

    /** The previous name of a class before it was renamed. Used by ClassListener and StatsTracker. */
    CLASS_OLD_NAME("Class Old Name"),

    /** The name of the invoked method or constructor. Used by ExecutionListener and StatsTracker. */
    METHOD_NAME("Method"),

    /** The exit status reported by BlueJ after a method invocation. Used by ExecutionListener and StatsTracker. */
    EXIT_STATUS("Exit Status"),

    /** Whether the compilation was triggered directly by the user. Used by CompilationListener and StatsTracker. */
    USER_GENERATED("User Generated"),

    /** The elapsed time in milliseconds for a compilation attempt. Used by CompilationListener and StatsTracker. */
    COMPILE_DURATION("Compile Duration"),

    /** The compiler error or warning message text. Used by CompilationListener and StatsTracker. */
    ERROR_MESSAGE("Error Message"),

    /** The source line number where a compiler error was reported. Used by CompilationListener and StatsTracker. */
    ERROR_LINE("Error Start Line"),

    /** The number of keystrokes in a typing burst. Used by EditorListener and StatsTracker. */
    KEYSTROKE_COUNT("Keystroke Count"),

    /** The duration in milliseconds of a typing burst. Used by EditorListener and StatsTracker. */
    BURST_DURATION("Burst Duration"),

    /** The typing cadence in keystrokes per minute for a burst. Used by EditorListener and StatsTracker. */
    CADENCE_KPM("Cadence KPM"),

    /** The running total of keystrokes recorded across the entire session. Used by EditorListener and StatsTracker. */
    SESSION_KEYSTROKES("Total Session Keystrokes"),

    /** The duration in milliseconds of an idle period. Used by EditorListener and StatsTracker. */
    IDLE_DURATION("Idle Duration Time");

    /** The human-readable string stored as the key in a TelemetryEvent detail map. */
    private final String label;

    /**
     * Creates a DetailsKey with the given label string.
     *
     * @param label the string used as the map key in event detail maps
     */
    DetailsKey(String label) {
        this.label = label;
    }

    /**
     * Returns the human-readable label string for this key.
     *
     * @return the label string stored in event detail maps
     */
    public String label() {
        return label;
    }
}

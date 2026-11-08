package com.spyj.logger;

public enum DetailsKey {
    PROJECT_NAME("Project"),
    CLASS_NAME("Class"),
    CLASS_OLD_NAME("Class Old Name"),
    METHOD_NAME("Method"),
    EXIT_STATUS("Exit Status"),
    USER_GENERATED("User Generated"),
    COMPILE_DURATION("Compile Duration"),
    ERROR_MESSAGE("Error Message"),
    ERROR_LINE("Error Start Line"),
    KEYSTROKE_COUNT("Keystroke Count"),
    BURST_DURATION("Burst Duration"),
    CADENCE_KPM("Cadence KPM"),
    SESSION_KEYSTROKES("Total Session Keystrokes"),
    IDLE_DURATION("Idle Duration Time");

    private final String label;

    DetailsKey (String label) {
        this.label = label;
    }
    
    public String label () {
        return label;
    }
}

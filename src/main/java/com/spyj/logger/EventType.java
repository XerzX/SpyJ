package com.spyj.logger;

/**
 * Enumerates every kind of event that SpyJ can record during a BlueJ session.
 * Constants are grouped by domain: project lifecycle, class lifecycle, method
 * detection, editor interactions, compilation, and program execution.
 *
 * @author Mykel Dedier
 */
public enum EventType {

    /** A new BlueJ project was created. */
    PROJECT_CREATED,

    /** An existing BlueJ project was deleted. */
    PROJECT_DELETED,

    /** A BlueJ project was opened. */
    PROJECT_OPENED,

    /** A BlueJ project was closed. */
    PROJECT_CLOSED,

    /** A BlueJ project was modified (e.g. a class was added or removed). */
    PROJECT_MODIFIED,

    /** A new class file was detected for the first time. */
    CLASS_CREATED,

    /** A class file was deleted from the project. */
    CLASS_DELETED,

    /** A class file was opened in an editor window. */
    CLASS_OPENED,

    /** A class editor window was closed. */
    CLASS_CLOSED,

    /** A class file was modified or renamed. */
    CLASS_MODIFIED,

    /** A new method signature was detected in a class. */
    METHOD_DEFINED,

    /**
     * The user stopped typing for at least {@code IDLE_THRESHOLD_MS} milliseconds.
     * Recorded with the duration of the idle period.
     */
    EDITOR_IDLE,

    /**
     * A continuous sequence of keystrokes ended.
     * Recorded with keystroke count, duration, and cadence (KPM).
     */
    EDITOR_BURST,

    /** A compilation attempt completed without errors. */
    COMPILE_SUCCEEDED,

    /** A compilation attempt completed with errors. */
    COMPILE_FAILED,

    /** A compiler warning was reported for a specific line. */
    COMPILE_WARNING,

    /** A compiler error was reported for a specific line. */
    COMPILE_ERROR,

    /** A method or constructor invocation finished with a normal exit. */
    EXECUTION_SUCCEEDED,

    /** A method or constructor invocation finished with a runtime exception. */
    EXECUTION_EXCEPTION
}

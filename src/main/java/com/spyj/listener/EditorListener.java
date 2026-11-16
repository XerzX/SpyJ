package com.spyj.listener;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.spyj.logger.EventType;
import com.spyj.logger.SpyJLogger;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Monitors keyboard activity across all BlueJ editor windows by hooking directly into
 * the JavaFX window event system. Records {@code EDITOR_BURST} and {@code EDITOR_IDLE}
 * telemetry events based on typing patterns.
 *
 * <p>Because BlueJ does not expose a keystroke-level editor API, this listener attaches
 * a {@link KeyEvent#KEY_PRESSED} filter to every JavaFX {@link Stage} that matches the
 * BlueJ editor window title format. New windows are discovered automatically via a
 * {@link ListChangeListener} on {@link Window#getWindows()}.</p>
 *
 * <p><b>Burst model:</b> consecutive keystrokes in the same editor window form a
 * <em>burst</em>. A burst ends when the user is idle for more than
 * {@link #IDLE_THRESHOLD_MS} milliseconds, or when focus moves to a different editor
 * stage. Only bursts with at least {@link #MIN_BURST_KEYSTROKES} keystrokes and a
 * non-zero duration are recorded.</p>
 *
 * <p><b>Idle model:</b> a {@link PauseTransition} of {@link #IDLE_THRESHOLD_MS}
 * milliseconds is restarted on every qualifying keystroke. When it fires, the active burst
 * is flushed and the session enters idle state. The idle duration is recorded as an
 * {@code EDITOR_IDLE} event when typing resumes or when {@link #stop()} is called.</p>
 *
 * <p>All interaction with JavaFX state is performed on the JavaFX Application Thread.</p>
 *
 * @author Mykel Dedier
 */
public class EditorListener {

    /**
     * Milliseconds of inactivity after which the user is considered idle.
     * Also the duration of the {@link PauseTransition} used to detect idle periods.
     */
    private static final long IDLE_THRESHOLD_MS = 3000;

    /**
     * Minimum number of keystrokes required for a burst to be recorded.
     * Bursts shorter than this threshold are discarded.
     */
    private static final int MIN_BURST_KEYSTROKES = 2;

    /** The central logger bus used to publish captured events. */
    private final SpyJLogger sJLogger;

    /**
     * The set of {@link Stage}s that have already had a key filter attached.
     * Uses weak references so that closed windows are garbage-collected automatically.
     */
    private final Set<Stage> hookedStages = Collections.newSetFromMap(new WeakHashMap<>());

    /** The JavaFX timer that fires when the user has been idle for {@link #IDLE_THRESHOLD_MS} ms. */
    private PauseTransition idleTimer;

    /** The editor stage in which the current burst is taking place, or {@code null} if no burst is active. */
    private Stage activeBurstStage;

    /** The window title of the editor stage where the current burst started. */
    private String activeBurstEditorTitle;

    /** The timestamp of the first keystroke in the current burst, or {@code null} if no burst is active. */
    private Instant burstStartTime;

    /** The number of qualifying keystrokes recorded in the current burst. */
    private int burstKeyStrokeCount;

    /** The timestamp of the most recently processed keystroke. */
    private Instant lastKeyStrokeTime;

    /** Running total of all qualifying keystrokes recorded across the entire session. */
    private long totalKeystrokes;

    /** Cumulative idle time in milliseconds accumulated across the entire session. */
    private long totalIdleTimeMs;

    /** {@code true} when the session is currently in an idle state. */
    private boolean idle;

    /** The title of the editor window in which the user became idle. */
    private String idleEditorTitle;

    /**
     * Creates an EditorListener, initialises the idle timer, and begins monitoring
     * all currently open and future JavaFX windows.
     * Setup is deferred to the JavaFX Application Thread via {@link Platform#runLater(Runnable)}.
     *
     * @param sJLogger the central event logger
     */
    public EditorListener(SpyJLogger sJLogger) {
        this.sJLogger = sJLogger;

        Platform.runLater(() -> {
            initIdleTimer();
            startListeningToWindows();
        });
    }

    /**
     * Flushes any in-progress burst and records a final idle event if the session is currently idle.
     * Must be called by {@code SpyJ.terminate()} before reading the event store to ensure the final
     * burst and idle event are not missing from the report.
     *
     * <p>If called on the JavaFX Application Thread, the flush runs synchronously.
     * Otherwise it is scheduled via {@link Platform#runLater(Runnable)} and this method
     * blocks for up to 2 seconds using a {@link CountDownLatch} to ensure the flush
     * completes before the caller reads the event store.</p>
     */
    public void stop() {
        Runnable r = () -> {
            if (idleTimer != null) {
                idleTimer.stop();
            }
            flushActiveBurst();

            if (idle && lastKeyStrokeTime != null) {
                logIdle(Duration.between(lastKeyStrokeTime, Instant.now()).toMillis(),
                        idleEditorTitle, idleEditorTitle);
                idle = false;
            }
        };

        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            // Block until the FX flush completes so that terminate() reads a
            // fully-populated EventStore. Without this the final typing burst
            // could be missing from the report.
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                r.run();
                latch.countDown();
            });
            try {
                latch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Initialises the {@link PauseTransition} idle timer and sets its completion handler
     * to call {@link #onUserBecameIdle()}.
     * Must be called on the JavaFX Application Thread.
     */
    private void initIdleTimer() {
        idleTimer = new PauseTransition(javafx.util.Duration.millis(IDLE_THRESHOLD_MS));
        idleTimer.setOnFinished(e -> onUserBecameIdle());
    }

    /**
     * Attaches a key filter to all currently open {@link Stage}s and installs a
     * {@link ListChangeListener} to hook any stages that open in the future.
     * Must be called on the JavaFX Application Thread.
     */
    private void startListeningToWindows() {
        for (Window window : Window.getWindows()) {
            if (window instanceof Stage) {
                hookStage((Stage) window);
            }
        }

        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (Window added : change.getAddedSubList()) {
                        if (added instanceof Stage) {
                            hookStage((Stage) added);
                        }
                    }
                }
            }
        });
    }

    /**
     * Hooks a {@link Stage} by attaching a scene change listener and a key filter to its
     * current scene. Stages already in {@link #hookedStages} are skipped.
     *
     * @param stage the JavaFX stage to hook
     */
    private void hookStage(Stage stage) {
        if (!hookedStages.add(stage)) {
            return;
        }

        stage.sceneProperty().addListener((obs, oldScene, newScene) -> attachKeyFilter(stage, newScene));
        attachKeyFilter(stage, stage.getScene());
    }

    /**
     * Attaches a {@link KeyEvent#KEY_PRESSED} event filter to the given scene.
     * The filter calls {@link #handleKeystroke(Stage)} for every qualifying keystroke
     * received in an editor stage.
     *
     * @param stage the stage that owns the scene, used for editor-stage identification
     * @param scene the scene to attach the filter to; does nothing if {@code null}
     */
    private void attachKeyFilter(Stage stage, Scene scene) {
        if (scene == null) {
            return;
        }

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (isEditorStage(stage) && isTypingKey(event)) {
                handleKeystroke(stage);
            }
        });
    }

    /**
     * Returns {@code true} if the given stage is a BlueJ editor window.
     * Editor stages are identified by their title: the title must not start with
     * {@code "BlueJ:"} and must contain {@code "["} with a trailing {@code "]"}.
     *
     * @param stage the stage to test
     * @return {@code true} if the stage appears to be a BlueJ editor window
     */
    private boolean isEditorStage(Stage stage) {
        String title = stage.getTitle();
        if (title == null || title.startsWith("BlueJ:")) {
            return false;
        }
        return title.contains("[") && title.endsWith("]");
    }

    /**
     * Returns {@code true} if the given key event represents a character-producing or
     * editing keystroke that should count toward burst and keystroke metrics.
     *
     * <p>Included: printable characters, Backspace, Delete, Enter, Tab,
     * and paste/cut/undo/redo shortcuts (Ctrl+V, Ctrl+X, Ctrl+Z, Ctrl+Y).</p>
     * <p>Excluded: modifier keys, navigation keys, arrow keys, function keys, Caps Lock,
     * and Escape. All other Ctrl/shortcut combinations are also excluded.</p>
     *
     * @param e the key event to test
     * @return {@code true} if the key event should be counted as a typing keystroke
     */
    private boolean isTypingKey(KeyEvent e) {
        KeyCode k = e.getCode();

        if (k == KeyCode.BACK_SPACE || k == KeyCode.DELETE
                || k == KeyCode.ENTER || k == KeyCode.TAB) {
            return true;
        }

        if (k.isModifierKey() || k == KeyCode.CAPS || k == KeyCode.ESCAPE) {
            return false;
        }
        if (k.isArrowKey() || k.isNavigationKey() || k.isFunctionKey()) {
            return false;
        }

        if (e.isShortcutDown()) {
            return k == KeyCode.V || k == KeyCode.X || k == KeyCode.Z || k == KeyCode.Y;
        }

        return true;
    }

    /**
     * Processes a qualifying keystroke in an editor stage.
     * Restarts the idle timer, increments the session keystroke counter, records an idle
     * event if the session was idle, and either continues the active burst or starts a new
     * one if the stage has changed.
     *
     * @param stage the editor stage in which the keystroke occurred
     */
    private void handleKeystroke(Stage stage) {
        Instant now = Instant.now();
        String title = stage.getTitle();

        idleTimer.playFromStart();
        totalKeystrokes++;

        if (idle) {
            long idleDuration = Duration.between(lastKeyStrokeTime, now).toMillis();
            logIdle(idleDuration, idleEditorTitle, title);
            idle = false;
        }

        if (activeBurstStage != stage) {
            flushActiveBurst();
            activeBurstStage = stage;
            activeBurstEditorTitle = title;
            burstStartTime = now;
            burstKeyStrokeCount = 1;
        } else {
            burstKeyStrokeCount++;
        }

        lastKeyStrokeTime = now;
    }

    /**
     * Called when the idle timer fires, indicating the user has been inactive for at least
     * {@link #IDLE_THRESHOLD_MS} milliseconds. Flushes the active burst and marks the
     * session as idle so the next keystroke can record the idle duration.
     */
    private void onUserBecameIdle() {
        if (burstStartTime == null) {
            return;
        }

        idleEditorTitle = activeBurstEditorTitle;
        flushActiveBurst();
        idle = true;
    }

    /**
     * Records an {@code EDITOR_IDLE} event with the idle duration and running session idle total.
     * Accumulates the duration into {@link #totalIdleTimeMs}.
     *
     * @param idleDurationMs the duration of the idle period in milliseconds
     * @param editorTitle    the title of the editor window where idle started
     * @param resumedIn      the title of the editor window where typing resumed
     */
    private void logIdle(long idleDurationMs, String editorTitle, String resumedIn) {
        totalIdleTimeMs += idleDurationMs;

        HashMap<String, Object> idleDetails = new HashMap<>();
        idleDetails.put("Editor Title", editorTitle);
        idleDetails.put("Resumed In", resumedIn);
        idleDetails.put("Idle Duration Time", idleDurationMs);
        idleDetails.put("Total Session Idle Time", totalIdleTimeMs);

        sJLogger.recordEvent(EventType.EDITOR_IDLE, idleDetails);
    }

    /**
     * Records an {@code EDITOR_BURST} event for the current active burst if it meets the
     * minimum keystroke threshold and has a non-zero duration.
     * Resets all burst state fields to their zero/null values after recording
     * (or discarding) the burst.
     */
    private void flushActiveBurst() {
        if (burstStartTime != null
                && lastKeyStrokeTime != null
                && burstKeyStrokeCount >= MIN_BURST_KEYSTROKES) {

            long burstDurationMs = Duration.between(burstStartTime, lastKeyStrokeTime).toMillis();

            if (burstDurationMs > 0) {
                int intervals = burstKeyStrokeCount - 1;
                double avgIntervalMs = (double) burstDurationMs / intervals;
                double kpm = intervals / (burstDurationMs / 60000.0);

                HashMap<String, Object> burstDetails = new HashMap<>();
                burstDetails.put("Editor Title", activeBurstEditorTitle);
                burstDetails.put("Keystroke Count", burstKeyStrokeCount);
                burstDetails.put("Burst Duration", burstDurationMs);
                burstDetails.put("Cadence KPM", Math.round(kpm));
                burstDetails.put("Avg Inter-Keystroke Time", Math.round(avgIntervalMs));
                burstDetails.put("Total Session Keystrokes", totalKeystrokes);

                sJLogger.recordEvent(EventType.EDITOR_BURST, burstDetails);
            }
        }

        activeBurstStage = null;
        activeBurstEditorTitle = null;
        burstStartTime = null;
        burstKeyStrokeCount = 0;
    }
}

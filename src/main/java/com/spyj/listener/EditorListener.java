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

public class EditorListener {

    private static final long IDLE_THRESHOLD_MS = 3000;
    private static final int MIN_BURST_KEYSTROKES = 2;

    private final SpyJLogger sJLogger;
    
    private final Set<Stage> hookedStages = Collections.newSetFromMap(new WeakHashMap<>());

    private PauseTransition idleTimer;
    
    private Stage activeBurstStage;
    private String activeBurstEditorTitle;
    private Instant burstStartTime;
    private int burstKeyStrokeCount;

    private Instant lastKeyStrokeTime;
    private long totalKeystrokes;
    private long totalIdleTimeMs;
    private boolean idle;
    private String idleEditorTitle;

    public EditorListener(SpyJLogger sJLogger) {
        this.sJLogger = sJLogger;

        Platform.runLater(() -> {
            initIdleTimer();
            startListeningToWindows();
        });
    }
    
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

    private void initIdleTimer() {
        idleTimer = new PauseTransition(javafx.util.Duration.millis(IDLE_THRESHOLD_MS));
        idleTimer.setOnFinished(e -> onUserBecameIdle());
    }

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

    private void hookStage(Stage stage) {
        if (!hookedStages.add(stage)) {
            return;
        }

        stage.sceneProperty().addListener((obs, oldScene, newScene) -> attachKeyFilter(stage, newScene));
        attachKeyFilter(stage, stage.getScene());
    }

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

    private boolean isEditorStage(Stage stage) {
        String title = stage.getTitle();
        if (title == null || title.startsWith("BlueJ:")) {
            return false;
        }
        return title.contains("[") && title.endsWith("]");
    }

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

    private void onUserBecameIdle() {
        if (burstStartTime == null) {
            return;
        }

        idleEditorTitle = activeBurstEditorTitle;
        flushActiveBurst();
        idle = true;
    }

    private void logIdle(long idleDurationMs, String editorTitle, String resumedIn) {
        totalIdleTimeMs += idleDurationMs;

        HashMap<String, Object> idleDetails = new HashMap<>();
        idleDetails.put("Editor Title", editorTitle);
        idleDetails.put("Resumed In", resumedIn);
        idleDetails.put("Idle Duration Time", idleDurationMs);
        idleDetails.put("Total Session Idle Time", totalIdleTimeMs);

        sJLogger.recordEvent(EventType.EDITOR_IDLE, idleDetails);
    }

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

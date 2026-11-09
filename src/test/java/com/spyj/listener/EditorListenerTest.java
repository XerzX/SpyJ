package com.spyj.listener;

import com.spyj.logger.EventType;
import com.spyj.logger.SpyJLogger;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.*;

public class EditorListenerTest {
    private EditorListener listener;
    private SpyJLogger fakelogger;
    private MockedStatic<Platform> fakeplatform;
    private MockedStatic<Window> fakewindows;
    private MockedConstruction<PauseTransition> fakepauses;
    private ObservableList<Window> windowlist;
    private SimpleObjectProperty<Scene> sceneproperty;
    private Stage fakeStage;
    private Scene fakeScene;
    private KeyEvent fakeKey;
    private EventHandler<KeyEvent> keyhandler;
    private EventHandler<ActionEvent> idlehandler;

    @BeforeEach
    void setup() {
        fakelogger = mock(SpyJLogger.class);

        fakeplatform = mockStatic(Platform.class);
        fakeplatform.when(() -> Platform.runLater(any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        });
        fakeplatform.when(Platform::isFxApplicationThread).thenReturn(true);

        fakepauses = mockConstruction(PauseTransition.class, (fakepause, context) ->
            doAnswer(invocation -> {
                idlehandler = invocation.getArgument(0);
                return null;
            }).when(fakepause).setOnFinished(any()));

        windowlist = FXCollections.observableArrayList();
        fakewindows = mockStatic(Window.class);
        fakewindows.when(Window::getWindows).thenReturn(windowlist);

        sceneproperty = new SimpleObjectProperty<>();
        fakeStage = mock(Stage.class);
        fakeScene = mock(Scene.class);
        fakeKey = mock(KeyEvent.class);

        when(fakeStage.getTitle()).thenReturn("Editor [TestClass.java]");
        when(fakeStage.getScene()).thenReturn(fakeScene);
        when(fakeStage.sceneProperty()).thenReturn(sceneproperty);
        when(fakeKey.getCode()).thenReturn(KeyCode.A);

        doAnswer(invocation -> {
            keyhandler = invocation.getArgument(1);
            return null;
        }).when(fakeScene).addEventFilter(eq(KeyEvent.KEY_PRESSED), any());
    }

    @AfterEach
    void teardown() {
        fakewindows.close();
        fakepauses.close();
        fakeplatform.close();
    }

    private void startListening() {
        listener = new EditorListener(fakelogger);
        windowlist.add(fakeStage);
    }

    private void pressKey(KeyCode code) {
        when(fakeKey.getCode()).thenReturn(code);
        keyhandler.handle(fakeKey);
    }

    @Test
    void testTypingBurstRecorded() throws Exception {
        startListening();

        pressKey(KeyCode.A);
        Thread.sleep(20);
        pressKey(KeyCode.B);
        idlehandler.handle(null);

        verify(fakelogger, times(1)).recordEvent(eq(EventType.EDITOR_BURST), anyMap());
    }

    @Test
    void testSingleKeystrokeDoesNotRecordBurst() {
        startListening();

        pressKey(KeyCode.A);
        idlehandler.handle(null);

        verifyNoInteractions(fakelogger);
    }

    @Test
    void testIdlePeriodRecordedOnResume() throws Exception {
        startListening();

        pressKey(KeyCode.A);
        idlehandler.handle(null);
        Thread.sleep(20);
        pressKey(KeyCode.B);

        verify(fakelogger, times(1)).recordEvent(eq(EventType.EDITOR_IDLE), anyMap());
    }

    @Test
    void testStopRecordsActiveBurst() throws Exception {
        startListening();

        pressKey(KeyCode.A);
        Thread.sleep(20);
        pressKey(KeyCode.B);
        listener.stop();

        verify(fakelogger, times(1)).recordEvent(eq(EventType.EDITOR_BURST), anyMap());
    }

    @Test
    void testStopWhileIdleRecordsIdle() {
        startListening();

        pressKey(KeyCode.A);
        idlehandler.handle(null);
        listener.stop();

        verify(fakelogger, times(1)).recordEvent(eq(EventType.EDITOR_IDLE), anyMap());
    }

    @Test
    void testBlueJStageIgnored() {
        when(fakeStage.getTitle()).thenReturn("BlueJ: TestProject");
        startListening();

        pressKey(KeyCode.A);
        pressKey(KeyCode.B);
        idlehandler.handle(null);

        verifyNoInteractions(fakelogger);
    }

    @Test
    void testStageWithoutEditorTitleIgnored() {
        when(fakeStage.getTitle()).thenReturn("TestClass");
        startListening();

        pressKey(KeyCode.A);
        pressKey(KeyCode.B);
        idlehandler.handle(null);

        verifyNoInteractions(fakelogger);
    }

    @Test
    void testNonTypingKeysIgnored() {
        startListening();

        pressKey(KeyCode.SHIFT);
        pressKey(KeyCode.LEFT);
        pressKey(KeyCode.F5);
        pressKey(KeyCode.ESCAPE);
        pressKey(KeyCode.CAPS);
        idlehandler.handle(null);

        verifyNoInteractions(fakelogger);
    }

    @Test
    void testEditingKeysCounted() throws Exception {
        startListening();

        pressKey(KeyCode.BACK_SPACE);
        Thread.sleep(20);
        pressKey(KeyCode.ENTER);
        idlehandler.handle(null);

        verify(fakelogger, times(1)).recordEvent(eq(EventType.EDITOR_BURST), anyMap());
    }

    @Test
    void testShortcutPasteCounted() throws Exception {
        when(fakeKey.isShortcutDown()).thenReturn(true);
        startListening();

        pressKey(KeyCode.V);
        Thread.sleep(20);
        pressKey(KeyCode.V);
        idlehandler.handle(null);

        verify(fakelogger, times(1)).recordEvent(eq(EventType.EDITOR_BURST), anyMap());
    }

    @Test
    void testShortcutCopyIgnored() {
        when(fakeKey.isShortcutDown()).thenReturn(true);
        startListening();

        pressKey(KeyCode.C);
        pressKey(KeyCode.C);
        idlehandler.handle(null);

        verifyNoInteractions(fakelogger);
    }

    @Test
    void testSceneAssignedAfterHooking() throws Exception {
        when(fakeStage.getScene()).thenReturn(null);
        startListening();
        sceneproperty.set(fakeScene);

        pressKey(KeyCode.A);
        Thread.sleep(20);
        pressKey(KeyCode.B);
        idlehandler.handle(null);

        verify(fakelogger, times(1)).recordEvent(eq(EventType.EDITOR_BURST), anyMap());
    }

    @Test
    void testExistingWindowsHookedOnStart() throws Exception {
        windowlist.add(mock(Window.class));
        windowlist.add(fakeStage);
        listener = new EditorListener(fakelogger);

        pressKey(KeyCode.A);
        Thread.sleep(20);
        pressKey(KeyCode.B);
        idlehandler.handle(null);

        verify(fakelogger, times(1)).recordEvent(eq(EventType.EDITOR_BURST), anyMap());
    }

    @Test
    void testStageHookedOnlyOnce() {
        startListening();
        windowlist.remove(fakeStage);
        windowlist.add(fakeStage);

        verify(fakeScene, times(1)).addEventFilter(eq(KeyEvent.KEY_PRESSED), any());
    }
}
package com.spyj.format;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class CommandInvokerTest {

    @Test
    void executesSubmittedCommand() throws Exception {
        AtomicBoolean ran = new AtomicBoolean(false);

        try (CommandInvoker invoker = new CommandInvoker()) {
            Future<Void> result = invoker.submit(() -> ran.set(true));
            assertNull(result.get(5, TimeUnit.SECONDS));
        }

        assertTrue(ran.get());
    }

    @Test
    void commandsRunInSubmissionOrder() throws Exception {
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());

        try (CommandInvoker invoker = new CommandInvoker()) {
            invoker.submit(() -> order.add(1));
            invoker.submit(() -> order.add(2));
            invoker.submit(() -> order.add(3)).get(5, TimeUnit.SECONDS);
        }

        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void commandFailureIsReportedThroughFuture() throws Exception {
        Command failing = () -> {
            throw new IOException("boom");
        };

        try (CommandInvoker invoker = new CommandInvoker()) {
            Future<Void> result = invoker.submit(failing);

            ExecutionException error = assertThrows(ExecutionException.class,
                    () -> result.get(5, TimeUnit.SECONDS));

            assertTrue(error.getCause() instanceof IOException);
            assertEquals("boom", error.getCause().getMessage());
        }
    }

    @Test
    void commandsRunOnABackgroundThread() throws Exception {
        AtomicReference<Thread> workerThread = new AtomicReference<>();

        try (CommandInvoker invoker = new CommandInvoker()) {
            invoker.submit(() -> workerThread.set(Thread.currentThread())).get(5, TimeUnit.SECONDS);
        }

        assertNotNull(workerThread.get());
        assertNotSame(Thread.currentThread(), workerThread.get());
    }

    @Test
    void nullCommandIsRejected() {
        try (CommandInvoker invoker = new CommandInvoker()) {
            assertThrows(NullPointerException.class, () -> invoker.submit(null));
        }
    }

    @Test
    void submitAfterCloseIsRejected() {
        CommandInvoker invoker = new CommandInvoker();
        invoker.close();

        assertThrows(RejectedExecutionException.class, () -> invoker.submit(() -> { }));
    }

    @Test
    void closeWaitsForSubmittedWorkToFinish() {
        AtomicBoolean finished = new AtomicBoolean(false);
        CommandInvoker invoker = new CommandInvoker();

        invoker.submit(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finished.set(true);
        });
        invoker.close();

        assertTrue(finished.get());
    }
}
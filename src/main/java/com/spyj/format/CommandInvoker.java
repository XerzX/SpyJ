package com.spyj.format;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// Runs export commands on one background thread.
public class CommandInvoker implements AutoCloseable {

    // Commands run one at a time in the order submitted.
    private final ExecutorService worker =Executors.newSingleThreadExecutor();

    // Queues a command and returns a way to check its result.
    public Future<Void> submit(Command command) {Objects.requireNonNull(command);

        return worker.submit(() -> {
            command.execute();
            return null;
        });
    }

    @Override
    public void close() {
        // Stops accepting commands and waits for queued work to finish.
        worker.close();
    }
}

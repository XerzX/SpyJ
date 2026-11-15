package com.spyj.format;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
/**
 * Runs commands one at a time on a background thread.
 * Acts as the invoker in the Command pattern.
 *
 * @author Caitlyn Maharaj
 */
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
  /**
     * Stops accepting commands and waits for submitted work to finish.
     * The calling thread waits until the worker has terminated.
     */
    @Override
    public void close() {
        
        worker.close();
    }
}

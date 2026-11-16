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

    /**
 * Executes submitted commands sequentially on a single background thread.
 */
    private final ExecutorService worker =Executors.newSingleThreadExecutor();

    /**
 * Submits a command for execution on the background thread.
 * Execution failures are reported through the returned future.
 *
 * @param command the command to execute
 * @return a future that completes with null when the command succeeds
 * @throws NullPointerException if command is null
 * @throws java.util.concurrent.RejectedExecutionException
 *         if the executor cannot accept the command, such as after closing
 */
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

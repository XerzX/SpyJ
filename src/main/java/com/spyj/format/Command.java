package com.spyj.format;

import java.io.IOException;

/**
 * Defines an operation that can be executed as a command.
 * ExportCommand implements this interface to save reports.
 *
 * @author Caitlyn Maharaj
 */
public interface Command {

    /**
     * Performs the command's operation.
     *
     * @throws IOException if an input/output error occurs
     */
    void execute() throws IOException;
}

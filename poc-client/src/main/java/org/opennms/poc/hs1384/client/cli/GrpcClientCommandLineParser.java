/*
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2023 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2023 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *
 */

package org.opennms.poc.hs1384.client.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.springframework.stereotype.Component;

@Component
public class GrpcClientCommandLineParser {

    public static final int DEFAULT_NUM_ITERATIONS = 1;
    public static final int DEFAULT_NUM_THREADS = 10;
    public static final boolean DEFAULT_EXECUTE_ASYNC = false;
    public static final int DEFAULT_ITERATION_DELAY = 0;

    private int numIterations = DEFAULT_NUM_ITERATIONS;
    private int iterationDelay = DEFAULT_ITERATION_DELAY;
    private int numThreads = DEFAULT_NUM_THREADS;
    private boolean executeAsync = DEFAULT_EXECUTE_ASYNC;
    private GrpcTestOperation testOperation = GrpcTestOperation.NORMAL_CLIENT_EXECUTION;

//========================================
// Getters and Setters
//----------------------------------------

    public int getNumIterations() {
        return numIterations;
    }

    public int getIterationDelay() {
        return iterationDelay;
    }

    public int getNumThreads() {
        return numThreads;
    }

    public boolean isExecuteAsync() {
        return executeAsync;
    }

    public GrpcTestOperation getTestOperation() {
        return testOperation;
    }

//========================================
// Interface
//----------------------------------------

    public void parseCommandLine(String... args) {
        Options options = new Options();
        this.prepareOptions(options);

        CommandLineParser commandLineParser = new DefaultParser();
        try {
            CommandLine commandLine = commandLineParser.parse(options, args);
            this.processCommandLine(commandLine);
        } catch (Exception exc) {
            showUsage(options);
            throw new RuntimeException("Failed to parse the command line arguments", exc);
        }
    }

//========================================
// Internals
//----------------------------------------

    private void prepareOptions(Options options) {
        options.addOption(
                new Option("d", "iteration-delay", true, "Amount of delay between iterations, in milliseconds")
        );
        options.addOption(
                new Option("n", "num-iteration", true, "Number of iterations to execute")
        );
        options.addOption(
                new Option("a", "async", false, "Execute operations asynchronously")
        );
        options.addOption(
                new Option("s", "sync", false, "Execute operations synchronously")
        );
        options.addOption(
                new Option("t", "num-thread", true, "Number of threads to use (-x)")
        );
        options.addOption(
                new Option("x", "spam-channel-get-state", false, "Operate by spamming the channel getState call")
        );
    }

    private void processCommandLine(CommandLine commandLine) {
        String textValue;

        for (Option oneOption : commandLine.getOptions()) {
            switch (oneOption.getOpt()) {
                case "d":
                    textValue = oneOption.getValue();
                    this.iterationDelay = Integer.parseInt(textValue);
                    break;

                case "n":
                    textValue = oneOption.getValue();
                    this.numIterations = Integer.parseInt(textValue);
                    break;

                case "a":
                    this.executeAsync = true;
                    break;

                case "s":
                    this.executeAsync = false;
                    break;

                case "t":
                    textValue = oneOption.getValue();
                    this.numThreads = Integer.parseInt(textValue);
                    break;

                case "x":
                    this.testOperation = GrpcTestOperation.SPAM_CHANNEL_GET_STATE;
                    break;
            }
        }
    }

    private void showUsage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("poc-client", options);
    }
}

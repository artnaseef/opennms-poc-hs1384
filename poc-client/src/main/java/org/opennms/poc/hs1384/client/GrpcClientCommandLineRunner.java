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

package org.opennms.poc.hs1384.client;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.opennms.poc.hs1384.client.cli.GrpcClientCommandLineParser;
import org.opennms.poc.hs1384.grpc.TestRequest;
import org.opennms.poc.hs1384.grpc.TestResponse;
import org.opennms.poc.hs1384.grpc.TestServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GrpcClientCommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcClientCommandLineRunner.class);

    @Value("${grpc.host:localhost}")
    private String grpcHost;

    @Value("${grpc.port:9990}")
    private int grpcPort;

    @Autowired
    private GrpcClientCommandLineParser grpcClientCommandLineParser;

    private ManagedChannel channel;
    private TestServiceGrpc.TestServiceStub serviceStub;
    private LoggingStreamObserver<TestResponse> loggingStreamObserver = new LoggingStreamObserver("test-response");
    private SimpleReconnectStrategy simpleReconnectStrategy;
    private ExecutorService executorService = Executors.newFixedThreadPool(3);
    private StreamObserver<TestRequest> minionToCloudStream;

    private AtomicInteger channelGetStateConcurrentCallCount = new AtomicInteger(0);
    private CountDownLatch channelSpamStartLatch;

//========================================
// Command-Line Interface
//----------------------------------------

    public void run(String... args) {
        try {
            setup();

            this.grpcClientCommandLineParser.parseCommandLine(args);

            switch (this.grpcClientCommandLineParser.getTestOperation()) {
                case NORMAL_CLIENT_EXECUTION -> this.executeNormalClient();
                case SPAM_CHANNEL_GET_STATE -> this.spamChannelGetState();
            }
        } catch (Exception exc) {
            LOG.error("GRPC client failure", exc);
        }
    }

//========================================
// Operations
//----------------------------------------

    private void executeNormalClient() throws Exception {
        int cur = 0;
        int iterationDelay = this.grpcClientCommandLineParser.getIterationDelay();
        while (cur < this.grpcClientCommandLineParser.getNumIterations()) {
            cur++;

            LOG.info("ITERATION {}", cur);

            if (this.grpcClientCommandLineParser.isExecuteAsync()) {
                executeClientAsync(cur);
            } else {
                executeClient(cur);
            }

            if (iterationDelay > 0 ) {
                delay(iterationDelay);
            }
        }

        LOG.info("Client execution complete; waiting 10 seconds before shutdown");
        delay(10_000);
    }

    private void spamChannelGetState() {
        // 10 threads concurrently requesting channel.getState()
        int numThread = grpcClientCommandLineParser.getNumThreads();

        channelSpamStartLatch = new CountDownLatch(numThread);

        int curThread = 0;
        while (curThread < numThread) {
            final int finalThreadNum = curThread;
            var thread = new Thread(() -> runChannelSpammer(finalThreadNum));
            thread.start();

            curThread++;
        }
    }

    private void runChannelSpammer(int threadNumber) {
        LOG.info("Starting Channel getState spam thread {} -- LATCH wait", threadNumber);

        channelSpamStartLatch.countDown();
        try {
            channelSpamStartLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        LOG.info("Starting Channel getState spam thread {} -- LATCH complete", threadNumber);

        int iter = 0;
        while (iter < grpcClientCommandLineParser.getNumIterations()) {
            // channel.getState(true);
            concurrentCheckCallChannelGetState();
            delay(1_000);

            iter++;
        }

        LOG.info("Shutting down Channel getState spam thread {}", threadNumber);
    }

//========================================
// Internals
//----------------------------------------

    private void setup() throws IOException {
        NettyChannelBuilder channelBuilder = NettyChannelBuilder.forAddress(grpcHost, grpcPort)
                .keepAliveWithoutCalls(true)
                .maxInboundMessageSize(1_000_000);

        channel = channelBuilder.usePlaintext().build();
        simpleReconnectStrategy = new SimpleReconnectStrategy(channel, this::handleConnect, this::handleDisconnect);
        simpleReconnectStrategy.activate();

        serviceStub = TestServiceGrpc.newStub(channel);
    }

    private void concurrentCheckCallChannelGetState() {
        int count = channelGetStateConcurrentCallCount.incrementAndGet();
        if (count > 1) {
            LOG.info("CONCURRENT CALL COUNT = {}", count);
        }
        channel.getState(true);
        channelGetStateConcurrentCallCount.decrementAndGet();
    }

    private void executeClientAsync(int iteration) {
        this.executorService.submit(() -> this.executeClient(iteration));
    }

    private void executeClient(int iteration) {
        LOG.info("CLIENT EXECUTION - STARTING");
        serviceStub.request(
                TestRequest.newBuilder().setQuery("test-query #" + iteration).build(),
                this.loggingStreamObserver
        );
        LOG.info("CLIENT EXECUTION - COMPLETED");
    }

    private void setupMinionToCloudStream() {
        // Don't expect and responses...
        minionToCloudStream = serviceStub.minionToCloudMessages(new LoggingStreamObserver<>("MINION-TO-CLOUD-RESPONSE"));
        minionToCloudStream.onNext(
                TestRequest.newBuilder().setQuery("SETUP-MINION-TO-CLOUD-QUERY").build()
        );
        LOG.info("Initialized RPC stream");
    }

    private void handleConnect() {
        LOG.warn("Connection started");
        this.setupMinionToCloudStream();

        serviceStub.request(
                TestRequest.newBuilder().setQuery("ON-CONNECT-REQUEST").build(),
                this.loggingStreamObserver
        );
    }

    private void handleDisconnect() {
        LOG.warn("Connection dropped");
    }

    private void delay(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException intExc) {
            LOG.info("delay interrupted", intExc);
        }
    }
}

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
import org.apache.commons.cli.CommandLine;
import org.opennms.poc.hs1384.grpc.TestRequest;
import org.opennms.poc.hs1384.grpc.TestResponse;
import org.opennms.poc.hs1384.grpc.TestServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

//========================================
// Command-Line Interface
//----------------------------------------

    public void run(String... args) {
        try {
            setup();

            this.grpcClientCommandLineParser.parseCommandLine(args);

            int cur = 0;
            while (cur < this.grpcClientCommandLineParser.getNumIterations()) {
                cur++;

                if (this.grpcClientCommandLineParser.isExecuteAsync()) {
                    executeClientAsync(cur);
                } else {
                    executeClient(cur);
                }
            }

            LOG.info("Client execution complete; waiting 10 seconds before shutdown");
            Thread.sleep(10_000);
        } catch (Exception exc) {
            LOG.error("GRPC client failure", exc);
        }
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

    private void executeClientAsync(int iteration) {
        this.executorService.submit(() -> this.executeClient(iteration));
    }

    private void executeClient(int iteration) {
        serviceStub.request(
                TestRequest.newBuilder().setQuery("test-query #" + iteration).build(),
                this.loggingStreamObserver
        );
    }

    private void handleConnect() {
        LOG.warn("Connection started");
        serviceStub.request(
                TestRequest.newBuilder().setQuery("ON-CONNECT-REQUEST").build(),
                this.loggingStreamObserver
        );
    }

    private void handleDisconnect() {
        LOG.warn("Connection dropped");
    }

}

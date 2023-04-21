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

package org.opennms.poc.hs1384;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.checkerframework.checker.units.qual.A;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WARNING: this implementation uses basic, primitive thread handling.  You have been warned.
 */
@Component
public class ConnectThenFailGrpcServer {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectThenFailGrpcServer.class);

    @Autowired
    private TestService testService;

    @Value("${ctf-server.port:9991}")
    private int port = 9991;

    @Autowired
    private Http2FrameFormatter http2FrameFormatter;

    private ServerSocket serverSocket;
    private AtomicBoolean shutdown = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        try {
            LOG.info("Starting CTF server on port {}", port);

            serverSocket = new ServerSocket(port);
            Thread serverThread = new Thread(this::runServer);

            serverThread.start();
        } catch (Exception ioExc) {
            LOG.error("Failed to startup the CTF server", ioExc);
            throw new RuntimeException(ioExc);
        }
    }

    private void runServer() {
        while (! shutdown.get()) {
            try {
                Socket connectionSocket = serverSocket.accept();
                Thread newConnectionThread = new Thread(() -> runOneConnection(connectionSocket));
                newConnectionThread.start();
            } catch (Exception exc) {
                LOG.error("CTF Server Error", exc);
            }
        }
    }

    private void runOneConnection(Socket socket) {
        try {
            sendSettings(socket);
        } catch (IOException ioException) {
            LOG.error("SETTINGS send failed", ioException);
        }

        while (! shutdown.get()) {
            try {
                LOG.info("SENDING GOAWAY TO CLIENT ON SOCKET {}", socket.getRemoteSocketAddress());
                sendGoAway(socket);
                Thread.sleep(60_000);
            } catch (Exception exc) {
                LOG.error("CTF Server Error", exc);
                return;
            }
        }

        try {
            socket.close();
        } catch (IOException ioExc) {
            LOG.info("SOCKET exception on shutdown", ioExc);
        }
    }

    private void sendSettings(Socket socket) throws IOException {
        byte[] frame = http2FrameFormatter.formatSettingsFrame();

        socket.getOutputStream().write(frame);
        socket.getOutputStream().flush();
    }

    private void sendGoAway(Socket socket) throws IOException {
        byte[] frame = http2FrameFormatter.formatGoAwayFrame();

        socket.getOutputStream().write(frame);
        socket.getOutputStream().flush();
    }
}

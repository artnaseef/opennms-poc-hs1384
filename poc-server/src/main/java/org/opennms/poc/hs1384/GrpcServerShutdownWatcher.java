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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wait for the GRPC server to shutdown in a non-daemon thread so the application will remain running until the
 *  GRPC server is stopped.
 */
public class GrpcServerShutdownWatcher {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcServerShutdownWatcher.class);

    private Thread runnerThread;
    private AtomicBoolean shutdownInd = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        runnerThread = new Thread(this::watch, "GRPC Server Watch Thread");
        runnerThread.setDaemon(false);

        runnerThread.start();

        LOG.info("STARTED GRPC SERVER WATCHER");
    }

    @PreDestroy
    public void shutdown() {
        this.shutdownInd.set(true);
        this.runnerThread.interrupt();
    }

//========================================
// Internals
//----------------------------------------

    private void watch() {
        LOG.info("STARTED WATCH RUNNER");
        while (shutdownInd.get()) {
            LOG.debug("shutdown ind = false");

            try {
                Thread.sleep(3_000);
            } catch (InterruptedException intExc) {
                LOG.debug("watcher thread interrupted", intExc);
            }
        }

        LOG.debug("shutdown ind = true");
    }
}

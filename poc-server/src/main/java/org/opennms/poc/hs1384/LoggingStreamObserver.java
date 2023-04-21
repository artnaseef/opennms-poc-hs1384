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

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingStreamObserver<T> implements StreamObserver<T> {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingStreamObserver.class);

    private final String label;

    public LoggingStreamObserver(String label) {
        this.label = label;
    }

    @Override
    public void onNext(T value) {
        LOG.info("STREAM {} NEXT VALUE: {}", this.label, value);
    }

    @Override
    public void onError(Throwable t) {
        LOG.error("STREAM {} ERROR", this.label, t);
    }

    @Override
    public void onCompleted() {
        LOG.info("STREAM {} COMPLETED", this.label);
    }
}

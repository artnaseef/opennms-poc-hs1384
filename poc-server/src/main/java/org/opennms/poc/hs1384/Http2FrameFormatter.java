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

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

// HTTP Frame {
//   Length (24),
//   Type (8),
//
//   Flags (8),
//
//   Reserved (1),
//   Stream Identifier (31),
//
//   Frame Payload (..),
// }
@Component
public class Http2FrameFormatter {

    public byte[] formatGoAwayFrame() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // LENGTH = 8 (FOR some reason, the client code rejects 0 and expects at least 8)
        byteArrayOutputStream.write(0);
        byteArrayOutputStream.write(0);
        byteArrayOutputStream.write(8);

        // TYPE = 7 (goaway)
        byteArrayOutputStream.write(7);

        // FLAGS = 0
        byteArrayOutputStream.write(0);

        // STREAM IDENTIFIER
        byteArrayOutputStream.write(0);
        byteArrayOutputStream.write(0);
        byteArrayOutputStream.write(0);
        byteArrayOutputStream.write(0);

        // Debug data (padding - see the length comment)
        for (int paddingCounter = 0; paddingCounter < 8; paddingCounter++) {
            byteArrayOutputStream.write(0);
        }

        return byteArrayOutputStream.toByteArray();
    }

    /**
     * SETTINGS Frame {
     *   Length (24),
     *   Type (8) = 0x04,
     *
     *   Unused Flags (7),
     *   ACK Flag (1),
     *
     *   Reserved (1),
     *   Stream Identifier (31) = 0,
     *
     *   Setting (48) ...,
     * }
     *
     * Setting {
     *   Identifier (16),
     *   Value (32),
     * }
     *
     * @return
     */
    public byte[] formatSettingsFrame() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // LENGTH = 6
        byteArrayOutputStream.write(0);
        byteArrayOutputStream.write(0);
        byteArrayOutputStream.write(6);

        // TYPE = 4 (settings)
        byteArrayOutputStream.write(4);

        // FLAGS = 0 (ACK Flag = 0)
        byteArrayOutputStream.write(0);

        // STREAM IDENTIFIER
        byteArrayOutputStream.write(0);
        byteArrayOutputStream.write(0);
        byteArrayOutputStream.write(0);
        byteArrayOutputStream.write(0);

        // SETTINGS_MAX_CONCURRENT_STREAMS
        byteArrayOutputStream.write(0);
        byteArrayOutputStream.write(3);
        byteArrayOutputStream.write(0x7f);
        byteArrayOutputStream.write(0xff);
        byteArrayOutputStream.write(0xff);
        byteArrayOutputStream.write(0xff);


        return byteArrayOutputStream.toByteArray();
    }
}

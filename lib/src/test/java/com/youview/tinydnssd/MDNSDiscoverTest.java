/* The MIT License (MIT)
 * Copyright (c) 2015 YouView Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.youview.tinydnssd;

import junit.framework.TestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static com.youview.tinydnssd.MDNSDiscover.*;
import static org.junit.Assert.*;

@RunWith(BlockJUnit4ClassRunner.class)
public class MDNSDiscoverTest extends TestCase {
    class ByteBuilder {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        ByteBuilder ascii(String str) {
            try {
                bos.write(str.getBytes());
            } catch (IOException e) {
                throw new Error(e);
            }
            return this;
        }

        ByteBuilder hex(String hex) {
            // hex digits must come in pairs, pairs optionally separated by spaces
            if (!hex.matches("( *[0-9a-f]{2})* *")) {
                throw new IllegalArgumentException();
            }
            for (int i = 0, l = hex.length(); i < l; i++) {
                if (hex.charAt(i) != ' ') {
                    String x = hex.substring(i, i + 2);
                    bos.write(Integer.valueOf(x, 16));
                    i++;
                }
            }
            return this;
        }

        byte[] build() {
            return bos.toByteArray();
        }
    }

    @Test
    public void testDiscoverPacket() throws IOException {
        byte[] actual = queryPacket("_example._tcp.local", QCLASS_INTERNET | CLASS_FLAG_UNICAST, QTYPE_PTR);
        byte[] expected = new ByteBuilder()
                .hex("00 00 00 00 00 01 00 00 00 00 00 00")
                .hex("08").ascii("_example")
                .hex("04").ascii("_tcp")
                .hex("05").ascii("local")
                .hex("00 00 0c 80 01")
                .build();
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testResolvePacket() throws IOException {
        byte[] actual = queryPacket("device-1234._example._tcp.local", QCLASS_INTERNET | CLASS_FLAG_UNICAST, QTYPE_TXT, QTYPE_SRV);
        byte[] expected = new ByteBuilder()
                .hex("00 00 00 00 00 02 00 00 00 00 00 00")
                .hex("0b").ascii("device-1234")
                .hex("08").ascii("_example")
                .hex("04").ascii("_tcp")
                .hex("05").ascii("local")
                .hex("00 00 10 80 01 c0 0c 00 21 80 01")
                .build();
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testReplyPacket() throws IOException {
        byte[] packet = createReplyPacket();
        Result r = decode(packet, packet.length);

        // check A record
        assertEquals("192.168.1.100", r.a.ipaddr);
        assertEquals("dev0123456789.local", r.a.fqdn);
        assertEquals(10, r.a.ttl);

        // check SRV record
        assertEquals(0, r.srv.priority);
        assertEquals(0, r.srv.weight);
        assertEquals(1234, r.srv.port);
        assertEquals("dev0123456789.local", r.srv.target);
        assertEquals("device-1234._example._tcp.local", r.srv.fqdn);
        assertEquals(10, r.srv.ttl);

        // check TXT record
        Map<String, String> expectedTxt = new HashMap<>();
        expectedTxt.put("foo", "bar");
        expectedTxt.put("bar", null);
        expectedTxt.put("txtvers", "1");
        assertEquals(expectedTxt, r.txt.dict);
        assertEquals("device-1234._example._tcp.local", r.txt.fqdn);
        assertEquals(10, r.txt.ttl);
    }

    @Test
    public void testTruncatedReplyPacketsWithResizedArray() {
        byte[] packet = createReplyPacket();
        for (int truncatedLength = 0; truncatedLength < packet.length; truncatedLength++) {
            byte[] truncatedPacket = Arrays.copyOf(packet, truncatedLength);
            try {
                decode(truncatedPacket, truncatedPacket.length);
                fail("decoding a truncated packet did not throw IOException");
            } catch (IOException e) {
                // this is OK...
            }
        }
    }

    @Test
    public void testTruncatedReplyPacketsWithOriginalArray() {
        byte[] packet = createReplyPacket();
        for (int truncatedLength = 0; truncatedLength < packet.length; truncatedLength++) {
            try {
                decode(packet, truncatedLength);
                fail("decoding a truncated packet did not throw IOException");
            } catch (IOException e) {
                // this is OK...
            }
        }
    }

    @Test(expected=EOFException.class)
    public void testAbortOnPointerOutOfRange() throws IOException {
        byte[] packet = new ByteBuilder()
                .hex("0000 8400")
                .hex("0000") // 0 questions
                .hex("0001") // 1 answer
                .hex("0000") // 0 authority RRs
                .hex("0000") // 0 additional RRs

                // 1st answer
                .hex("04").ascii("TEST")
                .hex("ffff")
                .hex("0001 0001")   // type=A, aclass=INTERNET
                .hex("0000000a 0004")   // ttl=10, length=4
                .hex("c0 a8 01 64")    // 192.168.1.100
                .build();
        decode(packet, packet.length);
    }

    @Test(expected=IOException.class)
    public void testAbortOnInfiniteDomainName() throws IOException {
        byte[] packet = new ByteBuilder()
                .hex("0000 8400")
                .hex("0000") // 0 questions
                .hex("0001") // 1 answer
                .hex("0000") // 0 authority RRs
                .hex("0000") // 0 additional RRs

                // 1st answer
                .hex("04").ascii("TEST")
                .hex("c00c")    // pointer to "TEST" => this encodes the infinite domain "TEST.TEST.TEST..."
                .hex("0001 0001")   // type=A, aclass=INTERNET
                .hex("0000000a 0004")   // ttl=10, length=4
                .hex("c0 a8 01 64")    // 192.168.1.100
                .build();
        decode(packet, packet.length);
    }

    @Test(expected=IOException.class)
    public void testAbortOnCyclicEmptyDomainName() throws IOException {
        byte[] packet = new ByteBuilder()
                .hex("0000 8400")
                .hex("0000") // 0 questions
                .hex("0001") // 1 answer
                .hex("0000") // 0 authority RRs
                .hex("0000") // 0 additional RRs

                // 1st answer
                .hex("04").ascii("TEST")
                // now follows an infinite loop of pointers:
                .hex("c013")    // [0011] => 0013
                .hex("c011")    // [0013] => 0011
                .hex("0001 0001")   // type=A, aclass=INTERNET
                .hex("0000000a 0004")   // ttl=10, length=4
                .hex("c0 a8 01 64")    // 192.168.1.100
                .build();
        decode(packet, packet.length);
    }

    @Test
    public void testDecodeRandomPackets() {
        final int SEED = 0x5FC45975;
        final int NUM_PACKETS = 10000;
        final int MAX_PACKET_LENGTH = 1536;
        Random random = new Random(SEED);
        for (int i = 0; i < NUM_PACKETS; i++) {
            int packetLength = random.nextInt(MAX_PACKET_LENGTH+1);
            byte[] packet = new byte[packetLength];
            random.nextBytes(packet);
            try {
                decode(packet, packetLength);
                fail("decoding a random packet did not throw IOException");
            } catch (IOException e) {
                // this is OK...
            }
        }
    }

    private byte[] createReplyPacket() {
        return new ByteBuilder()
                .hex("0000 8400")
                .hex("0002") // 2 questions
                .hex("0003") // 3 answers
                .hex("0000") // 0 authority RRs
                .hex("0000") // 0 additional RRs

                // 1st question
                .hex("0b").ascii("device-1234")
                .hex("08").ascii("_example")
                .hex("04").ascii("_tcp")
                .hex("05").ascii("local")
                .hex("00")
                .hex("0010 8001")   // type=TXT, qclass=UNICAST|INTERNET

                // 2nd question
                .hex("c00c")    // pointer to "device-1234._example._tcp.local"
                .hex("0021 8001")   // type=SRV, qclass=UNICAST|INTERNET

                // 1st answer
                .hex("c00c")    // pointer to "device-1234._example._tcp.local"
                .hex("0021 0001")   // type=SRV, aclass=INTERNET
                .hex("0000000a 0016")   // ttl=10, length=0x16
                .hex("0000 0000 04d2")  // priority=0, weight=0, port=1234
                .hex("0d").ascii("dev0123456789")    // target
                .hex("c026")    // pointer to ".local"

                // 2nd answer
                .hex("c049")    // pointer to "dev0123456789.local"
                .hex("0001 0001")   // type=A, aclass=INTERNET
                .hex("0000000a 0004")   // ttl=10, length=4
                .hex("c0 a8 01 64")    // 192.168.1.100

                // 3rd answer
                .hex("c00c")    // pointer to "device-1234._example._tcp.local"
                .hex("0010 0001")   // type=TXT, aclass=INTERNET
                .hex("0000000a 0022")   // ttl=10, length=0x22
                .hex("07").ascii("foo=bar")
                .hex("03").ascii("bar")
                .hex("0b").ascii("foo=ignored")    // the FIRST instance of foo takes precedence
                .hex("09").ascii("txtvers=1")
                .build();
    }
}

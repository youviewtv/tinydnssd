package com.example.slilly.nsddemo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by slilly on 12/05/2015.
 */
public class MDNSDiscover {

    private static final short QTYPE_A   = 0x0001;
    private static final short QTYPE_PTR = 0x000c;
    private static final short QTYPE_TXT = 0x0010;
    private static final short QTYPE_SRV = 0x0021;

    private static final short QCLASS_INTERNET = 0x0001;
    private static final short CLASS_FLAG_MULTICAST = 0, CLASS_FLAG_UNICAST = (short) 0x8000;
    private static final int PORT = 5353;

    public static void main(String[] args) throws IOException {
        discover("_yv-bridge._tcp.local");
//        discover("_googlecast._tcp.local");
    }

    public static void discover(String serviceType) throws IOException {
        InetAddress group = InetAddress.getByName("224.0.0.251");
        MulticastSocket sock = new MulticastSocket();   // binds to a random free source port
        System.out.println("Source port is " + sock.getLocalPort());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeInt(0);
        dos.writeShort(1);  // questions
        dos.writeShort(0);  // answers
        dos.writeShort(0);  // nscount
        dos.writeShort(0);  // arcount
        writeFQDN(serviceType, dos);
        dos.writeShort(QTYPE_PTR);
        dos.writeShort(QCLASS_INTERNET | CLASS_FLAG_UNICAST);
        dos.close();
        byte[] data = bos.toByteArray();
        System.out.println("Query packet:");
        hexdump(data, 0, data.length);
        DatagramPacket packet = new DatagramPacket(data, data.length, group, PORT);
        sock.send(packet);
        byte[] buf = new byte[1024];
        packet = new DatagramPacket(buf, buf.length);
        while (true) {
            sock.receive(packet);
            System.out.println("\n\nIncoming packet:");
            hexdump(packet.getData(), 0, packet.getLength());
            decode(packet.getData(), packet.getLength());
        }
    }

    private static void writeFQDN(String name, OutputStream out) throws IOException {
        for (String part : name.split("\\.")) {
            out.write(part.length());
            out.write(part.getBytes());
        }
        out.write(0);
    }

    private static void hexdump(byte[] data, int offset, int length) {
        while (offset < length) {
            System.out.printf("%08x", offset);
            int origOffset = offset;
            int col;
            for (col = 0; col < 16 && offset < length; col++, offset++) {
                System.out.printf(" %02x", data[offset] & 0xFF);
            }
            for (; col < 16; col++) {
                System.out.printf("   ");
            }
            System.out.print(" ");
            offset = origOffset;
            for (col = 0; col < 16 && offset < length; col++, offset++) {
                byte val = data[offset];
                char c;
                if (val >= 32 && val < 127) {
                    c = (char) val;
                } else {
                    c = '.';
                }
                System.out.printf("%c", c);
            }
            System.out.println();
        }
    }

    static void decode(byte[] packet, int packetLength) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet, 0, packetLength));
        short transactionID = dis.readShort();
        short flags = dis.readShort();
        int questions = dis.readUnsignedShort();
        int answers = dis.readUnsignedShort();
        int authorityRRs = dis.readUnsignedShort();
        int additionalRRs = dis.readUnsignedShort();
        // decode the queries
        for (int i = 0; i < questions; i++) {
            String fqdn = decodeFQDN(dis, packet, packetLength);
            short type = dis.readShort();
            short qclass = dis.readShort();
        }
        // decode the answers
        for (int i = 0; i < answers + authorityRRs + additionalRRs; i++) {
            String fqdn = decodeFQDN(dis, packet, packetLength);
            short type = dis.readShort();
            short aclass = dis.readShort();
            System.out.printf("%s record%n", typeString(type));
            System.out.println("Name: " + fqdn);
            int ttl = dis.readInt();
            int length = dis.readUnsignedShort();
            byte[] data = new byte[length];
            dis.readFully(data);
            switch (type) {
                case QTYPE_A:
                    System.out.println(decodeA(data));
                    break;
                case QTYPE_SRV:
                    decodeSRV(data, packet, packetLength);
                    break;
                case QTYPE_PTR:
                    System.out.println(decodePTR(data, packet, packetLength));
                    break;
                case QTYPE_TXT:
                    System.out.println(decodeTXT(data));
                    break;
                default:
                    hexdump(data, 0, data.length);
                    break;
            }
        }
    }

    private static void decodeSRV(byte[] srvData, byte[] packetData, int packetLength) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(srvData));
        int priority = dis.readUnsignedShort();
        int weight = dis.readUnsignedShort();
        int port = dis.readUnsignedShort();
        String target = decodeFQDN(dis, packetData, packetLength);
        System.out.printf("Priority: %d Weight: %d Port: %d Target: %s%n", priority, weight, port, target);
    }

    private static String typeString(short type) {
        switch (type) {
            case QTYPE_A:
                return "A";
            case QTYPE_PTR:
                return "PTR";
            case QTYPE_SRV:
                return "SRV";
            case QTYPE_TXT:
                return "TXT";
            default:
                return "Unknown";
        }
    }

    private static String decodePTR(byte[] ptrData, byte[] packet, int packetLength) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(ptrData));
        return decodeFQDN(dis, packet, packetLength);
    }

    private static String decodeA(byte[] data) throws IOException {
        if (data.length < 4) throw new IOException("expected 4 bytes for IPv4 addr");
        return (data[0] & 0xFF) + "." + (data[1] & 0xFF) + "." + (data[2] & 0xFF) + "." + (data[3] & 0xFF);
    }

    private static Map<String, String> decodeTXT(byte[] data) throws IOException {
        Map<String, String> result = new HashMap<>();
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        while (true) {
            int length;
            try {
                length = dis.readUnsignedByte();
            } catch (EOFException e) {
                return result;
            }
            byte[] segmentBytes = new byte[length];
            dis.readFully(segmentBytes);
            String segment = new String(segmentBytes);
            int pos = segment.indexOf('=');
            if (pos != -1) {
                String key = segment.substring(0, pos);
                String value = segment.substring(pos + 1);
                result.put(key, value);
            }
        }
    }

    private static String decodeFQDN(DataInputStream dis, byte[] packet, int packetLength) throws IOException {
        StringBuilder result = new StringBuilder();
        boolean dot = false;
        while (true) {
            int length = dis.readUnsignedByte();
            if (length == 0) break;
            if ((length & 0xc0) == 0xc0) {
                // this is a compression method, the remainder of the string is a pointer to elsewhere in the packet
                // adjust the stream boundary and repeat processing
                length &= 0x3f;
                int offset = (length << 8) | dis.readUnsignedByte();
                dis = new DataInputStream(new ByteArrayInputStream(packet, offset, packetLength - offset));
                continue;
            }
            byte[] segment = new byte[length];
            dis.readFully(segment);
            if (dot) result.append('.');
            dot = true;
            result.append(new String(segment));
        }
        return result.toString();
    }
}

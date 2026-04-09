/*
 * This file is part of the MissingMeteor distribution (https://github.com/nxghtCry0/missingmeteor).
 * Copyright (c) nxghtCry0.
 */

package meteordevelopment.meteorclient.systems.modules.misc.swarm;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Binary protocol definition for HiveMind swarm communication.
 * Message format: [1 byte type][4 byte payload length][payload bytes]
 */
public class SwarmProtocol {

    public static final int PROTOCOL_VERSION = 2;
    public static final int MAGIC = 0x484D; // "HM"

    // Message Types
    public static final byte HANDSHAKE          = 0x00; // Worker -> Host: version. Host -> Worker: workerId
    public static final byte COMMAND             = 0x01; // Host -> Worker: command string
    public static final byte POSITION_UPDATE     = 0x02; // Worker -> Host: x, y, z, yaw, pitch
    public static final byte BLOCK_CLAIM         = 0x03; // Worker -> Host: x, y, z
    public static final byte BLOCK_RELEASE       = 0x04; // Worker -> Host: x, y, z
    public static final byte CLAIM_TABLE_SYNC    = 0x05; // Host -> Worker: full claim table
    public static final byte WORKER_LIST         = 0x06; // Host -> Worker: all worker states
    public static final byte GROUP_ASSIGN        = 0x07; // Bidirectional: groupName
    public static final byte INVENTORY_UPDATE    = 0x08; // Worker -> Host: inventory snapshot
    public static final byte INVENTORY_REQUEST   = 0x09; // Host -> Worker: request item for another worker
    public static final byte DROP_ITEM           = 0x0A; // Host -> Worker: drop specific slot
    public static final byte PATH_UPDATE         = 0x0B; // Worker -> Host: goal x, y, z
    public static final byte PATH_CONFLICT       = 0x0C; // Host -> Worker: conflict warning
    public static final byte VEIN_CLAIM          = 0x0D; // Worker -> Host: list of block positions
    public static final byte ENTITY_SPOTTED      = 0x0E; // Worker -> Host: entity info
    public static final byte PING                = 0x0F; // Bidirectional: timestamp
    public static final byte PONG                = 0x10; // Bidirectional: timestamp
    public static final byte DISCONNECT          = 0x11; // Either direction: reason string

    // --- Frame I/O ---

    public static void writeFrame(DataOutput out, byte type, byte[] payload) throws IOException {
        out.writeByte(type);
        out.writeInt(payload.length);
        out.write(payload);
    }

    public static void writeFrame(DataOutput out, byte type) throws IOException {
        writeFrame(out, type, new byte[0]);
    }

    public static byte readType(DataInput in) throws IOException {
        return in.readByte();
    }

    public static byte[] readPayload(DataInput in) throws IOException {
        int len = in.readInt();
        byte[] data = new byte[len];
        in.readFully(data);
        return data;
    }

    // --- Serialization Helpers ---

    // Handshake: worker sends [version:int]
    public static byte[] serializeHandshake(int version) {
        return writeInt(version);
    }

    // Handshake response: host sends [workerId:int]
    public static int deserializeHandshake(byte[] data) {
        return readInt(data, 0);
    }

    // Command: [string]
    public static byte[] serializeCommand(String command) {
        return writeString(command);
    }

    public static String deserializeCommand(byte[] data) {
        return readString(data, 0);
    }

    // Position: [x:double, y:double, z:double, yaw:float, pitch:float]
    public static byte[] serializePosition(double x, double y, double z, float yaw, float pitch) {
        byte[] buf = new byte[32];
        putDouble(buf, 0, x);
        putDouble(buf, 8, y);
        putDouble(buf, 16, z);
        putFloat(buf, 24, yaw);
        putFloat(buf, 28, pitch);
        return buf;
    }

    public static double[] deserializePosition(byte[] data) {
        return new double[] {
            getDouble(data, 0), getDouble(data, 8), getDouble(data, 16),
            getFloat(data, 24), getFloat(data, 28)
        };
    }

    // Block position: [x:int, y:int, z:int]
    public static byte[] serializeBlockPos(int x, int y, int z) {
        byte[] buf = new byte[12];
        putInt(buf, 0, x);
        putInt(buf, 4, y);
        putInt(buf, 8, z);
        return buf;
    }

    public static int[] deserializeBlockPos(byte[] data) {
        return new int[] { readInt(data, 0), readInt(data, 4), readInt(data, 8) };
    }

    // Claim table sync: [count:int][x:int,y:int,z:int,workerId:int]...
    public static byte[] serializeClaimTable(Map<Long, Integer> claims) {
        byte[] buf = new byte[4 + claims.size() * 16];
        putInt(buf, 0, claims.size());
        int i = 4;
        for (Map.Entry<Long, Integer> entry : claims.entrySet()) {
            long key = entry.getKey();
            int x = (int) (key >> 32);
            int z = (int) key;
            int y = (int) (entry.getValue() >> 16);
            int workerId = entry.getValue() & 0xFFFF;
            putInt(buf, i, x); i += 4;
            putInt(buf, i, y); i += 4;
            putInt(buf, i, z); i += 4;
            putInt(buf, i, workerId); i += 4;
        }
        return buf;
    }

    public static Map<Long, Integer> deserializeClaimTable(byte[] data) {
        Map<Long, Integer> claims = new HashMap<>();
        int count = readInt(data, 0);
        int i = 4;
        for (int c = 0; c < count; c++) {
            int x = readInt(data, i); i += 4;
            int y = readInt(data, i); i += 4;
            int z = readInt(data, i); i += 4;
            int packed = readInt(data, i); i += 4;
            int workerId = packed & 0xFFFF;
            long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
            int value = ((y & 0xFFFF) << 16) | (workerId & 0xFFFF);
            claims.put(key, value);
        }
        return claims;
    }

    // Group assign: [groupName:string]
    public static byte[] serializeGroupAssign(String group) {
        return writeString(group);
    }

    public static String deserializeGroupAssign(byte[] data) {
        return readString(data, 0);
    }

    // Inventory update: [slotCount:int][slot:int,itemId:int,count:int]...
    public static byte[] serializeInventory(int[] slots, int[] itemIds, int[] counts) {
        byte[] buf = new byte[4 + slots.length * 12];
        putInt(buf, 0, slots.length);
        int i = 4;
        for (int s = 0; s < slots.length; s++) {
            putInt(buf, i, slots[s]); i += 4;
            putInt(buf, i, itemIds[s]); i += 4;
            putInt(buf, i, counts[s]); i += 4;
        }
        return buf;
    }

    public static int[][] deserializeInventory(byte[] data) {
        int count = readInt(data, 0);
        int[] slots = new int[count];
        int[] itemIds = new int[count];
        int[] counts = new int[count];
        int i = 4;
        for (int s = 0; s < count; s++) {
            slots[s] = readInt(data, i); i += 4;
            itemIds[s] = readInt(data, i); i += 4;
            counts[s] = readInt(data, i); i += 4;
        }
        return new int[][] { slots, itemIds, counts };
    }

    // Disconnect: [reason:string]
    public static byte[] serializeDisconnect(String reason) {
        return writeString(reason);
    }

    public static String deserializeDisconnect(byte[] data) {
        return readString(data, 0);
    }

    // --- Primitive Helpers ---

    private static byte[] writeInt(int value) {
        byte[] buf = new byte[4];
        putInt(buf, 0, value);
        return buf;
    }

    private static byte[] writeString(String str) {
        byte[] strBytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] buf = new byte[4 + strBytes.length];
        putInt(buf, 0, strBytes.length);
        System.arraycopy(strBytes, 0, buf, 4, strBytes.length);
        return buf;
    }

    public static String readString(byte[] data, int offset) {
        int len = readInt(data, offset);
        return new String(data, offset + 4, len, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static void putInt(byte[] buf, int offset, int value) {
        buf[offset]     = (byte) (value >>> 24);
        buf[offset + 1] = (byte) (value >>> 16);
        buf[offset + 2] = (byte) (value >>> 8);
        buf[offset + 3] = (byte) value;
    }

    private static void putFloat(byte[] buf, int offset, float value) {
        putInt(buf, offset, Float.floatToIntBits(value));
    }

    private static void putDouble(byte[] buf, int offset, double value) {
        long bits = Double.doubleToLongBits(value);
        buf[offset]     = (byte) (bits >>> 56);
        buf[offset + 1] = (byte) (bits >>> 48);
        buf[offset + 2] = (byte) (bits >>> 40);
        buf[offset + 3] = (byte) (bits >>> 32);
        buf[offset + 4] = (byte) (bits >>> 24);
        buf[offset + 5] = (byte) (bits >>> 16);
        buf[offset + 6] = (byte) (bits >>> 8);
        buf[offset + 7] = (byte) bits;
    }

    public static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) |
               ((data[offset + 1] & 0xFF) << 16) |
               ((data[offset + 2] & 0xFF) << 8) |
               (data[offset + 3] & 0xFF);
    }

    private static float getFloat(byte[] data, int offset) {
        return Float.intBitsToFloat(readInt(data, offset));
    }

    private static double getDouble(byte[] data, int offset) {
        long bits = ((long)(data[offset] & 0xFF) << 56) |
                    ((long)(data[offset + 1] & 0xFF) << 48) |
                    ((long)(data[offset + 2] & 0xFF) << 40) |
                    ((long)(data[offset + 3] & 0xFF) << 32) |
                    ((long)(data[offset + 4] & 0xFF) << 24) |
                    ((long)(data[offset + 5] & 0xFF) << 16) |
                    ((long)(data[offset + 6] & 0xFF) << 8) |
                    ((long)(data[offset + 7] & 0xFF));
        return Double.longBitsToDouble(bits);
    }

    /**
     * Encode block pos into a long key for the claim table.
     */
    public static long blockPosToKey(int x, int y, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * Encode y and workerId into an int value for the claim table.
     */
    public static int yPosAndWorkerToValue(int y, int workerId) {
        return ((y & 0xFFFF) << 16) | (workerId & 0xFFFF);
    }

    public static int getYFromValue(int value) {
        return (short) (value >> 16);
    }

    public static int getWorkerIdFromValue(int value) {
        return value & 0xFFFF;
    }

    // --- Public long helpers (for PING/PONG) ---

    public static void putLong(byte[] buf, int offset, long value) {
        buf[offset]     = (byte) (value >>> 56);
        buf[offset + 1] = (byte) (value >>> 48);
        buf[offset + 2] = (byte) (value >>> 40);
        buf[offset + 3] = (byte) (value >>> 32);
        buf[offset + 4] = (byte) (value >>> 24);
        buf[offset + 5] = (byte) (value >>> 16);
        buf[offset + 6] = (byte) (value >>> 8);
        buf[offset + 7] = (byte) value;
    }

    public static long readLong(byte[] data, int offset) {
        return ((long)(data[offset] & 0xFF) << 56) |
               ((long)(data[offset + 1] & 0xFF) << 48) |
               ((long)(data[offset + 2] & 0xFF) << 40) |
               ((long)(data[offset + 3] & 0xFF) << 32) |
               ((long)(data[offset + 4] & 0xFF) << 24) |
               ((long)(data[offset + 5] & 0xFF) << 16) |
               ((long)(data[offset + 6] & 0xFF) << 8) |
               ((long)(data[offset + 7] & 0xFF));
    }
}

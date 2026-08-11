package the.spoofies.communicateAPI;

import java.nio.charset.StandardCharsets;

public class PacketReader {

    private final byte[] data;
    private int pos = 0;

    public PacketReader(byte[] data) {
        this.data = data;
    }

    public int readVarInt() {
        int value = 0;
        int shift = 0;
        int b;
        do {
            b = data[pos++] & 0xFF;
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }

    public String readUtf() {
        int len = readVarInt();
        String s = new String(data, pos, len, StandardCharsets.UTF_8);
        pos += len;
        return s;
    }

    public boolean hasMore() {
        return pos < data.length;
    }
}
package the.spoofies.communicateAPI;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;


public final class ChatDataMessenger {

    private final String prefix;
    private final String delimiter;

    private ChatDataMessenger(String prefix, String delimiter) {
        if (prefix == null || prefix.isEmpty()) {
            throw new IllegalArgumentException("prefix must not be null or empty");
        }
        if (delimiter == null || delimiter.isEmpty()) {
            throw new IllegalArgumentException("delimiter must not be null or empty");
        }
        if (prefix.contains(delimiter)) {
            throw new IllegalArgumentException("prefix must not contain the delimiter");
        }
        this.prefix = prefix;
        this.delimiter = delimiter;
    }

    /** Creates a messenger with the default '_' delimiter. */
    public static ChatDataMessenger create(String prefix) {
        return new ChatDataMessenger(prefix, "_");
    }

    /** Creates a messenger with a custom delimiter, in case '_' collides with your data. */
    public static ChatDataMessenger create(String prefix, String delimiter) {
        return new ChatDataMessenger(prefix, delimiter);
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDelimiter() {
        return delimiter;
    }

    /**
     * Sends a data packet to a single player, immediately.
     *
     * @param player the recipient — only this player receives it
     * @param type   caller-defined packet type id, distinguishes what the payload means
     * @param fields any number of ordered payload values, joined with the delimiter
     */
    public void send(Player player, int type, Object... fields) {
        StringBuilder sb = new StringBuilder(prefix).append(delimiter).append(type);
        for (Object field : fields) {
            appendField(sb, field);
        }
        // Player#sendMessage sends an unsigned system-chat packet on Paper,
        // so there's no chat-signature/report metadata attached to it.
        player.sendMessage(sb.toString());
    }

    /** Starts a fluent packet builder for a given type, when you have many fields to add. */
    public Packet packet(int type) {
        return new Packet(type);
    }

    private void appendField(StringBuilder sb, Object field) {
        String s = String.valueOf(field);
        if (s.contains(delimiter)) {
            throw new IllegalArgumentException(
                    "Field '" + s + "' contains the delimiter '" + delimiter + "' and would corrupt the packet"
            );
        }
        sb.append(delimiter).append(s);
    }

    /** Fluent builder for packets with an arbitrary, caller-chosen number of fields. */
    public final class Packet {
        private final int type;
        private final List<Object> fields = new ArrayList<>();

        private Packet(int type) {
            this.type = type;
        }

        public Packet with(Object field) {
            fields.add(field);
            return this;
        }

        public void send(Player player) {
            ChatDataMessenger.this.send(player, type, fields.toArray());
        }
    }
}
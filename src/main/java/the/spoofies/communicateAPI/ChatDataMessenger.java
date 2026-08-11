package the.spoofies.communicateAPI;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ChatDataMessenger {

    // Matches Minecraft's channel identifier rules: lowercase namespace:path.
    private static final Pattern VALID_PREFIX = Pattern.compile("^[a-z0-9_\\-]+$");

    private final JavaPlugin owningPlugin;
    private final String channel;

    private ChatDataMessenger(JavaPlugin owningPlugin, String prefix) {
        if (owningPlugin == null) {
            throw new IllegalArgumentException("owningPlugin must not be null");
        }
        if (prefix == null || prefix.isEmpty()) {
            throw new IllegalArgumentException("prefix must not be null or empty");
        }
        if (!VALID_PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException(
                    "prefix must be lowercase letters, digits, '-' or '_' only (got: " + prefix + ")"
            );
        }

        this.owningPlugin = owningPlugin;
        this.channel = prefix + ":data";


        owningPlugin.getServer().getMessenger().registerOutgoingPluginChannel(owningPlugin, channel);
    }

    public static ChatDataMessenger create(JavaPlugin owningPlugin, String prefix) {
        return new ChatDataMessenger(owningPlugin, prefix);
    }

    public String getChannel() {
        return channel;
    }


    public void send(Player player, int type, Object... fields) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PacketWriter.writeVarInt(out, type);
            PacketWriter.writeVarInt(out, fields.length);
            for (Object field : fields) {
                PacketWriter.writeUtf(out, String.valueOf(field));
            }
            player.sendPluginMessage(owningPlugin, channel, out.toByteArray());
        } catch (IOException e) {

            throw new RuntimeException("Failed to encode packet for channel " + channel, e);
        }
    }

    public Packet packet(int type) {
        return new Packet(type);
    }

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
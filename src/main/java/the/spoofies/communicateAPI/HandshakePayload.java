package the.spoofies.communicateAPI;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class HandshakePayload implements Listener, PluginMessageListener {

    private static final String HANDSHAKE_CHANNEL = "communicateapi:handshake";

    private static Plugin owningPlugin;
    private static volatile boolean clientRequired = false;
    private static volatile long timeoutSeconds = 3;

    private final Map<UUID, CompletableFuture<Boolean>> awaitingResponse = new ConcurrentHashMap<>();

    public HandshakePayload() {}

    public static void register(Plugin plugin) {
        owningPlugin = plugin;
        HandshakePayload instance = new HandshakePayload();

        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, HANDSHAKE_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, HANDSHAKE_CHANNEL, instance);
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);

        plugin.getLogger().info("[Handshake] register() called, owningPlugin=" + plugin.getName());
    }

    public static void ensureClient(boolean required) {
        clientRequired = required;
    }

    public static void ensureClient(boolean required, long timeoutSecondsOverride) {
        clientRequired = required;
        timeoutSeconds = timeoutSecondsOverride;
    }

    @EventHandler
    void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        owningPlugin.getLogger().info("[Handshake] onConfigure fired, clientRequired=" + clientRequired);

        if (!clientRequired || owningPlugin == null) {
            owningPlugin.getLogger().info("[Handshake] onConfigure returning early");
            return;
        }

        PlayerConfigurationConnection connection = event.getConnection();
        UUID uuid = connection.getProfile().getId();
        owningPlugin.getLogger().info("[Handshake] uuid=" + uuid);
        if (uuid == null) return;

        CompletableFuture<Boolean> response = new CompletableFuture<>();
        response.completeOnTimeout(false, timeoutSeconds, TimeUnit.SECONDS);
        awaitingResponse.put(uuid, response);

        owningPlugin.getLogger().info("[Handshake] sending probe during configuration");
        try {
            connection.sendPluginMessage(owningPlugin, HANDSHAKE_CHANNEL, new byte[]{0});
            owningPlugin.getLogger().info("[Handshake] probe sent successfully");
        } catch (Exception e) {
            owningPlugin.getLogger().warning("[Handshake] FAILED to send probe: " + e);
        }

        boolean verified = response.join();
        owningPlugin.getLogger().info("[Handshake] verified=" + verified);

        if (!verified) {
            connection.disconnect(Component.text(
                    "This server requires a CommunicateAPI-compatible client mod to join."
            ));
        }

        awaitingResponse.remove(uuid);
    }

    @EventHandler
    void onConnectionClose(PlayerConnectionCloseEvent event) {
        awaitingResponse.remove(event.getPlayerUniqueId());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (HANDSHAKE_CHANNEL.equals(channel)) {
            complete(player.getUniqueId());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, PlayerConnection connection, byte[] message) {
        if (!HANDSHAKE_CHANNEL.equals(channel)) return;

        UUID uuid = resolveUuid(connection);
        if (uuid != null) {
            complete(uuid);
        }
    }

    private UUID resolveUuid(PlayerConnection connection) {
        if (connection instanceof PlayerConfigurationConnection configConnection) {
            return configConnection.getProfile().getId();
        }
        if (connection instanceof PlayerGameConnection gameConnection) {
            return gameConnection.getPlayer().getUniqueId();
        }
        return null;
    }

    private void complete(UUID uuid) {
        CompletableFuture<Boolean> future = awaitingResponse.get(uuid);
        if (future != null) {
            future.complete(true);
        }
    }
}
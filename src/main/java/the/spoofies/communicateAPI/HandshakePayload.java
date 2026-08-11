package the.spoofies.communicateAPI;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class HandshakePayload implements Listener {

    private static final String HANDSHAKE_CHANNEL = "communicateapi:handshake";

    private static Plugin owningPlugin;
    private static volatile boolean clientRequired = false;
    private static volatile long timeoutSeconds = 3;

    private final Map<UUID, CompletableFuture<Boolean>> awaitingResponse = new ConcurrentHashMap<>();

    public HandshakePayload() {}

    public static void register(Plugin plugin) {
        owningPlugin = plugin;
        HandshakePayload instance = new HandshakePayload();
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
        if (!clientRequired || owningPlugin == null) return;

        PlayerConfigurationConnection connection = event.getConnection();
        UUID uuid = connection.getProfile().getId();
        if (uuid == null) return;

        CompletableFuture<Boolean> response = new CompletableFuture<>();
        response.completeOnTimeout(false, timeoutSeconds, TimeUnit.SECONDS);
        awaitingResponse.put(uuid, response);

        // send the probe on our custom channel during configuration
        connection.sendPluginMessage(owningPlugin, HANDSHAKE_CHANNEL, new byte[]{0});

        // block here until the client answers or we time out
        boolean verified = response.join();

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

    // called from wherever your configuration-phase plugin message listener
    // receives the client's reply on HANDSHAKE_CHANNEL
    void markVerified(UUID uuid) {
        CompletableFuture<Boolean> future = awaitingResponse.get(uuid);
        if (future != null) {
            future.complete(true);
        }
    }
}
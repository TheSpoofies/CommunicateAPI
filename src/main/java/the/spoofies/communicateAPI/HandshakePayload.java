package the.spoofies.communicateAPI;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;


public final class HandshakePayload implements Listener, PluginMessageListener {

    private static final String HANDSHAKE_CHANNEL = "communicateapi:handshake";
    private static final long DEFAULT_TIMEOUT_TICKS = 60L; // 3 seconds

    private static Plugin owningPlugin;
    private static volatile boolean clientRequired = false;
    private static volatile long timeoutTicks = DEFAULT_TIMEOUT_TICKS;

    private static final Set<UUID> VERIFIED = ConcurrentHashMap.newKeySet();

    private HandshakePayload() {}

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
        log("[Handshake] ensureClient(" + required + ") called");
    }

    public static void ensureClient(boolean required, long timeoutTicksOverride) {
        clientRequired = required;
        timeoutTicks = timeoutTicksOverride;
        log("[Handshake] ensureClient(" + required + ", " + timeoutTicksOverride + ") called");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        log("[Handshake] onJoin fired, clientRequired=" + clientRequired + ", owningPlugin=" + owningPlugin);

        if (!clientRequired || owningPlugin == null) {
            log("[Handshake] onJoin returning early");
            return;
        }

        Player player = event.getPlayer();
        VERIFIED.remove(player.getUniqueId());

        // delay the probe — sending synchronously in onJoin can race the client's
        // channel registration and get silently dropped
        owningPlugin.getServer().getScheduler().runTaskLater(owningPlugin, () -> {
            if (!player.isOnline()) return;

            log("[Handshake] sending probe to " + player.getName() + " on channel " + HANDSHAKE_CHANNEL);
            try {
                player.sendPluginMessage(owningPlugin, HANDSHAKE_CHANNEL, new byte[]{0});
                log("[Handshake] probe send() returned without exception");
            } catch (Exception e) {
                log("[Handshake] FAILED to send probe: " + e);
            }
        }, 20L); // 1 second delay to be safe — you can tune this down later

        owningPlugin.getServer().getScheduler().runTaskLater(owningPlugin, () -> {
            log("[Handshake] kick check running for " + player.getName()
                    + ", online=" + player.isOnline()
                    + ", verified=" + VERIFIED.contains(player.getUniqueId()));

            if (!player.isOnline()) return;
            if (!VERIFIED.contains(player.getUniqueId())) {
                log("[Handshake] kicking " + player.getName());
                player.kick(Component.text(
                        "This server requires a CommunicateAPI-compatible client mod to join."
                ));
            }
        }, timeoutTicks + 20L); // push the kick check back by the same delay
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        VERIFIED.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        log("[Handshake] onPluginMessageReceived channel=" + channel + " player=" + player.getName());
        if (HANDSHAKE_CHANNEL.equals(channel)) {
            VERIFIED.add(player.getUniqueId());
            log("[Handshake] " + player.getName() + " verified");
        }
    }

    private static void log(String message) {
        Logger logger = owningPlugin != null ? owningPlugin.getLogger() : Logger.getLogger("Handshake");
        logger.info(message);
    }
}
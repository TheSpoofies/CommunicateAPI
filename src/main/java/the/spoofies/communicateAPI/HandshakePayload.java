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

/**
 * Client-presence handshake system. Kicks players who don't respond to a
 * probe packet on join, which only a CommunicateAPI-compatible client mod
 * ever replies to.
 *
 * Call {@link #register(Plugin)} once from your main class's onEnable, then
 * call {@link #ensureClient(boolean)} (from anywhere — your own plugin, or
 * any plugin depending on CommunicateAPI) to turn the requirement on or off.
 */
public final class HandshakePayload implements Listener, PluginMessageListener {

    private static final String HANDSHAKE_CHANNEL = "communicateapi:handshake";
    private static final long DEFAULT_TIMEOUT_TICKS = 60L; // 3 seconds

    private static Plugin owningPlugin;
    private static volatile boolean clientRequired = false;
    private static volatile long timeoutTicks = DEFAULT_TIMEOUT_TICKS;

    private static final Set<UUID> VERIFIED = ConcurrentHashMap.newKeySet();

    private HandshakePayload() {}

    /** Registers the channel and event listeners. Call once, from your plugin's onEnable. */
    public static void register(Plugin plugin) {
        owningPlugin = plugin;

        HandshakePayload instance = new HandshakePayload();
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, HANDSHAKE_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, HANDSHAKE_CHANNEL, instance);
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
    }

    /**
     * Controls whether players must have a CommunicateAPI-compatible client mod
     * installed to stay connected.
     *
     * When true: every joining player is sent a handshake request and has a
     * few seconds to reply before being kicked. Players without the mod never
     * reply, since nothing on their end recognizes the channel.
     */
    public static void ensureClient(boolean required) {
        clientRequired = required;
    }

    /** Same as {@link #ensureClient(boolean)}, with a custom timeout in ticks (20 ticks = 1 second). */
    public static void ensureClient(boolean required, long timeoutTicksOverride) {
        clientRequired = required;
        timeoutTicks = timeoutTicksOverride;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!clientRequired || owningPlugin == null) return;

        Player player = event.getPlayer();
        VERIFIED.remove(player.getUniqueId());

        // Empty payload — its arrival on this channel at all is the signal we need.
        player.sendPluginMessage(owningPlugin, HANDSHAKE_CHANNEL, new byte[0]);

        owningPlugin.getServer().getScheduler().runTaskLater(owningPlugin, () -> {
            if (!player.isOnline()) return;
            if (!VERIFIED.contains(player.getUniqueId())) {
                player.kick(Component.text(
                        "This server requires a CommunicateAPI-compatible client mod to join."
                ));
            }
        }, timeoutTicks);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        VERIFIED.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (HANDSHAKE_CHANNEL.equals(channel)) {
            VERIFIED.add(player.getUniqueId());
        }
    }
}
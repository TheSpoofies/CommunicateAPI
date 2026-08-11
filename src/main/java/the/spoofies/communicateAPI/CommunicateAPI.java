package the.spoofies.communicateAPI;

import org.bukkit.plugin.java.JavaPlugin;

public final class CommunicateAPI extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("CommunicateAPI v" + getPluginMeta().getVersion() + " enabled.");

    }

    @Override
    public void onDisable() {
        getLogger().info("CommunicateAPI disabled.");
    }
}

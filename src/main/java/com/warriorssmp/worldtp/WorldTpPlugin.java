package com.warriorssmp.worldtp;

import org.bukkit.plugin.java.JavaPlugin;

public class WorldTpPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        var command = getCommand("wtp");
        if (command != null) {
            command.setExecutor(new WorldTpCommand(this));
        }
        getServer().getPluginManager().registerEvents(new TeleportEventTracer(this), this);
        getLogger().info("WSMP-WorldTP enabled - /wtp <world> is a plain synchronous teleport, "
                + "not routed through Multiverse-Core's async teleporter. Teleport event tracing is active.");
    }
}

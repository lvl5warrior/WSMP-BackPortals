package com.warriorssmp.worldtp;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DIAGNOSTIC ONLY. Registers a handler at every single priority tier for
 * PlayerTeleportEvent (with ignoreCancelled=false on all of them, so we see the
 * state regardless of what's already happened) and logs event.isCancelled() at
 * each one. Since Bukkit fires handlers in a fixed order - LOWEST, LOW, NORMAL,
 * HIGH, HIGHEST, MONITOR - and we know exactly which plugin is registered at
 * each of those tiers (from the earlier /wtp debug listener dump), whichever
 * tier flips from false to true tells us exactly which plugin cancelled it.
 */
public class TeleportEventTracer implements Listener {

    private final JavaPlugin plugin;

    TeleportEventTracer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onLowest(PlayerTeleportEvent event) {
        log("LOWEST (before any plugin)", event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onLow(PlayerTeleportEvent event) {
        log("LOW (after WorldGuard)", event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onNormal(PlayerTeleportEvent event) {
        log("NORMAL (after WSMP-Teams and WSMP-Classes)", event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onHigh(PlayerTeleportEvent event) {
        log("HIGH (after Essentials)", event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHighest(PlayerTeleportEvent event) {
        log("HIGHEST (after Multiverse-Core)", event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onMonitor(PlayerTeleportEvent event) {
        log("MONITOR (after Multiverse-Portals, final state)", event);
    }

    private void log(String stage, PlayerTeleportEvent event) {
        String line = "[wtp-trace] " + stage + ": cancelled=" + event.isCancelled()
                + " cause=" + event.getCause()
                + " to=" + event.getTo();
        plugin.getLogger().info(line);
        event.getPlayer().sendMessage(org.bukkit.ChatColor.GOLD + line);
    }
}

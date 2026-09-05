package com.warriorssmp.worldtp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

/**
 * /wtp <world> - teleports the sender directly to the given world's configured
 * Multiverse spawn point, using a plain, synchronous Bukkit teleport.
 *
 * DIAGNOSTIC VERSION: logs every step (target resolution, source of the
 * coordinates, the teleport() call's own boolean result, and the player's
 * location before/immediately-after/1-tick-after/20-ticks-after) to both the
 * player and console, so a silent failure or snap-back is visible instead of
 * getting reported as a plain success.
 */
public class WorldTpCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    WorldTpCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /wtp <world>");
            return true;
        }

        String worldName = args[0];
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "World '" + worldName + "' isn't loaded or doesn't exist.");
            return true;
        }

        ResolvedSpawn resolved = resolveMultiverseSpawn(world);
        Location destination = resolved.location();

        Location before = player.getLocation().clone();

        sender.sendMessage(ChatColor.GRAY + "[wtp debug] source=" + resolved.source()
                + " target=" + formatLoc(destination));
        sender.sendMessage(ChatColor.GRAY + "[wtp debug] before=" + formatLoc(before));
        plugin.getLogger().info("[wtp] " + player.getName() + " requested /wtp " + worldName
                + " | source=" + resolved.source() + " target=" + formatLoc(destination)
                + " | before=" + formatLoc(before));

        boolean result;
        try {
            result = player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[wtp] teleport() threw an exception", e);
            sender.sendMessage(ChatColor.RED + "[wtp debug] teleport() threw: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            return true;
        }

        Location immediatelyAfter = player.getLocation().clone();
        sender.sendMessage(ChatColor.GRAY + "[wtp debug] teleport() returned " + result);
        sender.sendMessage(ChatColor.GRAY + "[wtp debug] immediately-after=" + formatLoc(immediatelyAfter));
        plugin.getLogger().info("[wtp] " + player.getName() + " teleport() returned " + result
                + " | immediately-after=" + formatLoc(immediatelyAfter));

        if (!result) {
            sender.sendMessage(ChatColor.RED
                    + "teleport() returned false - almost always means another plugin (WorldGuard, "
                    + "a region flag, a PlayerTeleportEvent listener, etc.) cancelled it. Check console "
                    + "for anything else logged at the same moment.");
        }

        // Check again a tick later and 20 ticks later, in case something snaps the
        // player back right after a teleport that otherwise reported success -
        // this is a different failure mode than teleport() returning false, and
        // would explain "it says it teleported me but it didn't".
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location oneTickLater = player.getLocation().clone();
            sender.sendMessage(ChatColor.GRAY + "[wtp debug] +1 tick=" + formatLoc(oneTickLater));
            plugin.getLogger().info("[wtp] " + player.getName() + " +1 tick=" + formatLoc(oneTickLater));
        }, 1L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location twentyTicksLater = player.getLocation().clone();
            sender.sendMessage(ChatColor.GRAY + "[wtp debug] +20 ticks=" + formatLoc(twentyTicksLater));
            plugin.getLogger().info("[wtp] " + player.getName() + " +20 ticks=" + formatLoc(twentyTicksLater));

            boolean stillNearTarget = twentyTicksLater.getWorld().equals(destination.getWorld())
                    && twentyTicksLater.distanceSquared(destination) < 100; // within 10 blocks
            if (result && !stillNearTarget) {
                sender.sendMessage(ChatColor.RED
                        + "You were snapped away from the destination shortly after teleporting - "
                        + "something else (another plugin's move/teleport listener) is moving you back. "
                        + "This is a different bug than the Multiverse async issue.");
            } else if (result && stillNearTarget) {
                sender.sendMessage(ChatColor.GREEN + "Teleport confirmed - still at destination 1 second later.");
            }
        }, 20L);

        return true;
    }

    private record ResolvedSpawn(Location location, String source) {}

    /**
     * Reads plugins/Multiverse-Core/worlds.yml directly as plain YAML (no
     * Multiverse-Core API dependency) and pulls out the spawn-location fields for
     * the given world's section, keyed by its namespaced key (e.g.
     * "minecraft:world_arena", or "minecraft:overworld" for the main world).
     */
    private ResolvedSpawn resolveMultiverseSpawn(World world) {
        File worldsFile = new File(plugin.getDataFolder().getParentFile(), "Multiverse-Core/worlds.yml");
        if (!worldsFile.exists()) {
            return new ResolvedSpawn(world.getSpawnLocation(), "vanilla-fallback (worlds.yml not found at "
                    + worldsFile.getAbsolutePath() + ")");
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(worldsFile);
        String key = world.getKey().toString();
        ConfigurationSection worldSection = config.getConfigurationSection(key);
        if (worldSection == null) {
            return new ResolvedSpawn(world.getSpawnLocation(),
                    "vanilla-fallback (no section '" + key + "' in worlds.yml)");
        }

        ConfigurationSection spawn = worldSection.getConfigurationSection("spawn-location");
        if (spawn == null) {
            return new ResolvedSpawn(world.getSpawnLocation(),
                    "vanilla-fallback (section '" + key + "' has no spawn-location)");
        }

        double x = spawn.getDouble("x", world.getSpawnLocation().getX());
        double y = spawn.getDouble("y", world.getSpawnLocation().getY());
        double z = spawn.getDouble("z", world.getSpawnLocation().getZ());
        float pitch = (float) spawn.getDouble("pitch", 0.0);
        float yaw = (float) spawn.getDouble("yaw", 0.0);

        return new ResolvedSpawn(new Location(world, x, y, z, yaw, pitch), "multiverse worlds.yml key '" + key + "'");
    }

    private String formatLoc(Location loc) {
        return String.format("world=%s x=%.2f y=%.2f z=%.2f pitch=%.2f yaw=%.2f",
                loc.getWorld() != null ? loc.getWorld().getName() : "null",
                loc.getX(), loc.getY(), loc.getZ(), loc.getPitch(), loc.getYaw());
    }
}
